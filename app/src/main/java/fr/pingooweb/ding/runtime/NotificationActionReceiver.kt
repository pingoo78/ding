package fr.pingooweb.ding.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.pingooweb.ding.DingApp

/** Actions des notifications (Pause/Reprendre, Passer, Arrêter, Valider, Couper). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val container = (context.applicationContext as DingApp).container
        val pending = goAsync()
        container.runController.handleAction(action).invokeOnCompletion { pending.finish() }
    }
}
