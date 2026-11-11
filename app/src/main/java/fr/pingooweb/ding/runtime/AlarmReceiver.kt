package fr.pingooweb.ding.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.pingooweb.ding.DingApp

/** Réveil à l'échéance : demande au contrôleur d'appliquer les étapes échues. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as DingApp).container
        val pending = goAsync()
        container.runController.onAlarm().invokeOnCompletion { pending.finish() }
    }
}
