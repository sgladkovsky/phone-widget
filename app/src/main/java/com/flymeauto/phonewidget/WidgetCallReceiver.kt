package com.flymeauto.phonewidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat

class WidgetCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_CALL) {
            return
        }

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        val config = WidgetPreferences.load(context, widgetId)
        if (config == null || config.phoneNumber.isBlank()) {
            Toast.makeText(context, R.string.widget_not_configured, Toast.LENGTH_SHORT).show()
            return
        }

        val phoneUri = Uri.parse("tel:${config.phoneNumber}")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val callIntent = Intent(Intent.ACTION_CALL, phoneUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            Toast.makeText(context, R.string.call_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_WIDGET_CALL = "com.flymeauto.phonewidget.ACTION_WIDGET_CALL"
    }
}
