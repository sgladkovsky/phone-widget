package com.flymeauto.phonewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class PhoneWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            WidgetPreferences.delete(context, widgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PhoneWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val config = WidgetPreferences.load(context, widgetId)
            val transparency = config?.transparencyPercent ?: WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val backgroundAlpha = (WidgetAppearance.opacity(transparency) * 255).toInt()

            views.setInt(R.id.widget_background, "setImageAlpha", backgroundAlpha)

            if (config == null) {
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_placeholder, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
                views.setViewVisibility(R.id.widget_placeholder, View.GONE)
                views.setContentDescription(R.id.widget_icon, config.displayName)

                when (config.iconType) {
                    IconType.CUSTOM -> {
                        val iconUri = WidgetIconStorage.iconUri(context, widgetId)
                        if (iconUri != null) {
                            views.setImageViewUri(R.id.widget_icon, iconUri)
                        } else {
                            views.setImageViewResource(
                                R.id.widget_icon,
                                WidgetIconMapper.drawableRes(IconType.PHONE)
                            )
                        }
                    }
                    else -> {
                        views.setImageViewResource(
                            R.id.widget_icon,
                            WidgetIconMapper.drawableRes(config.iconType)
                        )
                    }
                }
            }

            val callIntent = Intent(context, WidgetCallReceiver::class.java).apply {
                action = WidgetCallReceiver.ACTION_WIDGET_CALL
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
