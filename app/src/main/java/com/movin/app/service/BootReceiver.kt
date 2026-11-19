package com.movin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("movin_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("autostart_on_boot", false)) {
                MovinServerService.start(context)
            }
        }
    }
}
