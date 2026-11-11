package fr.pingooweb.ding

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import fr.pingooweb.ding.di.AppContainer

class DingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels(this)
        container.runController.restore()
    }

    companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_ALERT = "step_alert"

        fun createNotificationChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val running = NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.channel_running_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_running_description)
                setShowBadge(false)
            }
            // Le son et la vibration sont gérés par AlertPlayer (USAGE_ALARM) : le canal reste silencieux.
            val alert = NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alert_description)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannels(listOf(running, alert))
        }
    }
}
