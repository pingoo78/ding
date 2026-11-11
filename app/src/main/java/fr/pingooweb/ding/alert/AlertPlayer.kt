package fr.pingooweb.ding.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import fr.pingooweb.ding.domain.model.SoundChoice
import java.util.Locale

/** Paramètres d'une alerte sonore / vibrée / vocale. */
data class AlertRequest(
    val sound: SoundChoice,
    val vibrate: Boolean,
    /** Libellé à annoncer par synthèse vocale, ou null. */
    val speakLabel: String?,
    /** Joue en boucle jusqu'à [stop] (étape à valider), plafonné à [MAX_LOOP_MS]. */
    val loop: Boolean,
    /** Durée de sonnerie si [loop] est faux. */
    val durationMs: Long,
)

/**
 * Joue le son (USAGE_ALARM), la vibration et la synthèse vocale d'une alerte. Tout est appelé depuis le
 * thread principal. Une nouvelle alerte remplace la précédente. WakeLock partiel uniquement pendant la lecture.
 */
class AlertPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var generation = 0
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeech: String? = null
    private val stopRunnable = Runnable { stop() }
    private var current: AlertRequest? = null
    private var soundPaused = false

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun play(request: AlertRequest) {
        stop()
        val gen = ++generation
        current = request
        val totalMs = if (request.loop) MAX_LOOP_MS else request.durationMs.coerceAtLeast(1_000L)
        acquireWakeLock(totalMs)
        handler.postDelayed(stopRunnable, totalMs)

        if (request.vibrate) vibrate()

        val speak = request.speakLabel
        val silent = request.sound is SoundChoice.Silent
        if (!silent) {
            requestFocus()
            startSound(request.sound, gen, 0, candidates(request.sound))
        }
        if (speak != null) {
            if (silent) {
                speak(speak, gen)
            } else {
                // Court bip, puis annonce ; le son reprend à la fin de l'annonce.
                handler.postDelayed({
                    if (gen != generation) return@postDelayed
                    soundPaused = true
                    runCatching { player?.pause() }
                    speak(speak, gen)
                }, BEEP_MS)
            }
        }
    }

    /** Coupe son, vibration et voix ; libère toutes les ressources. */
    fun stop() {
        generation++
        handler.removeCallbacksAndMessages(null)
        current = null
        soundPaused = false
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        runCatching { vibrator?.cancel() }
        tts?.let { t ->
            runCatching { t.stop() }
            runCatching { t.shutdown() }
        }
        tts = null
        ttsReady = false
        pendingSpeech = null
        abandonFocus()
        releaseWakeLock()
    }

    // --- Son ------------------------------------------------------------------------------------

    private fun candidates(sound: SoundChoice): List<Uri> {
        val system = buildList {
            for (type in intArrayOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE)) {
                runCatching { RingtoneManager.getActualDefaultRingtoneUri(appContext, type) }.getOrNull()?.let { add(it) }
            }
            runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }.getOrNull()?.let { add(it) }
        }
        return when (sound) {
            is SoundChoice.Silent -> emptyList()
            is SoundChoice.SystemAlarm -> system
            is SoundChoice.Custom -> listOfNotNull(runCatching { Uri.parse(sound.uri) }.getOrNull()) + system
        }.distinct()
    }

    private fun startSound(sound: SoundChoice, gen: Int, index: Int, uris: List<Uri>) {
        if (gen != generation || index >= uris.size) return
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(alarmAttrs)
            mp.setDataSource(appContext, uris[index])
            mp.isLooping = true
            mp.setOnPreparedListener {
                if (gen != generation) {
                    runCatching { it.release() }
                    return@setOnPreparedListener
                }
                if (!soundPaused) it.start()
            }
            mp.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "Erreur MediaPlayer ($what/$extra) sur ${uris[index]}")
                runCatching { failed.release() }
                if (player === failed) player = null
                startSound(sound, gen, index + 1, uris)
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Son illisible : ${uris[index]}", e)
            runCatching { mp.release() }
            if (player === mp) player = null
            startSound(sound, gen, index + 1, uris)
        }
    }

    private fun resumeSound(gen: Int) {
        if (gen != generation) return
        soundPaused = false
        runCatching { player?.start() }
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAttrs)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    // --- Vibration ------------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            // Motif répété (pause, 500 ms, 400 ms) jusqu'à l'arrêt.
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0), alarmAttrs)
        }
    }

    // --- Synthèse vocale ------------------------------------------------------------------------

    private fun speak(text: String, gen: Int) {
        if (gen != generation) return
        if (ttsFailed && tts == null) {
            resumeSound(gen)
            return
        }
        if (ttsReady) {
            doSpeak(text, gen)
            return
        }
        pendingSpeech = text
        if (tts == null) {
            ttsFailed = false
            tts = TextToSpeech(appContext) { status ->
                handler.post {
                    if (gen != generation) return@post
                    val t = tts
                    val ok = status == TextToSpeech.SUCCESS && t != null &&
                        t.setLanguage(Locale.FRENCH).let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
                    if (!ok) {
                        Log.w(TAG, "TTS indisponible (status=$status)")
                        ttsFailed = true
                        pendingSpeech = null
                        resumeSound(gen)
                        return@post
                    }
                    t!!.setAudioAttributes(alarmAttrs)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) { handler.post { onSpeechEnd(gen) } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { handler.post { onSpeechEnd(gen) } }
                    })
                    ttsReady = true
                    pendingSpeech?.let { doSpeak(it, gen) }
                    pendingSpeech = null
                }
            }
        }
    }

    private fun doSpeak(text: String, gen: Int) {
        val t = tts ?: return
        val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ding-$gen")
        if (r != TextToSpeech.SUCCESS) onSpeechEnd(gen)
    }

    private fun onSpeechEnd(gen: Int) {
        if (gen != generation) return
        resumeSound(gen)
        val req = current ?: return
        // Alerte silencieuse à valider : on répète l'annonce régulièrement.
        val label = req.speakLabel
        if (req.sound is SoundChoice.Silent && req.loop && label != null) {
            handler.postDelayed({ if (gen == generation) speak(label, gen) }, REPEAT_SPEECH_MS)
        }
    }

    // --- WakeLock -------------------------------------------------------------------------------

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        runCatching {
            val wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ding:alert")
            wl.setReferenceCounted(false)
            wl.acquire(timeoutMs + 5_000L)
            wakeLock = wl
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    companion object {
        private const val TAG = "AlertPlayer"
        /** Sécurité : le son d'une alerte à valider s'arrête après 3 minutes. */
        const val MAX_LOOP_MS = 3 * 60 * 1000L
        private const val BEEP_MS = 1_200L
        private const val REPEAT_SPEECH_MS = 15_000L
    }
}
