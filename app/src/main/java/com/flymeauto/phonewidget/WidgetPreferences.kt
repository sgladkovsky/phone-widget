package com.flymeauto.phonewidget

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider

data class WidgetConfig(
    val contactName: String,
    val phoneNumber: String,
    val iconType: IconType,
    val customIconUri: String?,
    val confirmCall: Boolean = false,
    val transparencyPercent: Int = WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT
) {
    val displayName: String
        get() = contactName.ifBlank { phoneNumber }

    companion object {
        fun empty() = WidgetConfig("", "", IconType.PHONE, null, false, WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT)
    }
}

enum class IconType(val prefValue: String) {
    PHONE("phone"),
    PERSON("person"),
    HOME("home"),
    CAR("car"),
    WORK("work"),
    STAR("star"),
    HEART("heart"),
    CUSTOM("custom");

    companion object {
        fun fromPref(value: String?): IconType =
            entries.firstOrNull { it.prefValue == value } ?: PHONE
    }
}

object WidgetPreferences {
    private const val PREFS_NAME = "phone_widget_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        prefs(context).edit()
            .putString(key(widgetId, "name"), config.contactName)
            .putString(key(widgetId, "phone"), config.phoneNumber)
            .putString(key(widgetId, "icon_type"), config.iconType.prefValue)
            .putString(key(widgetId, "icon_uri"), config.customIconUri)
            .putBoolean(key(widgetId, "confirm_call"), config.confirmCall)
            .putInt(key(widgetId, "transparency"), config.transparencyPercent)
            .apply()
    }

    fun load(context: Context, widgetId: Int): WidgetConfig? {
        val preferences = prefs(context)
        val phone = preferences.getString(key(widgetId, "phone"), null)
        if (phone.isNullOrBlank()) {
            return null
        }
        return WidgetConfig(
            contactName = preferences.getString(key(widgetId, "name"), "") ?: "",
            phoneNumber = phone,
            iconType = IconType.fromPref(preferences.getString(key(widgetId, "icon_type"), null)),
            customIconUri = preferences.getString(key(widgetId, "icon_uri"), null),
            confirmCall = preferences.getBoolean(key(widgetId, "confirm_call"), false),
            transparencyPercent = preferences.getInt(
                key(widgetId, "transparency"),
                WidgetAppearance.DEFAULT_TRANSPARENCY_PERCENT
            )
        )
    }

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(key(widgetId, "name"))
            .remove(key(widgetId, "phone"))
            .remove(key(widgetId, "icon_type"))
            .remove(key(widgetId, "icon_uri"))
            .remove(key(widgetId, "confirm_call"))
            .remove(key(widgetId, "transparency"))
            .apply()
        WidgetIconStorage.deleteIcon(context, widgetId)
    }

    fun getAllWidgetIds(context: Context): List<Int> {
        return prefs(context).all.keys
            .mapNotNull { key ->
                if (key.startsWith("widget_") && key.endsWith("_phone")) {
                    key.removePrefix("widget_").removeSuffix("_phone").toIntOrNull()
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
    }

    private fun key(widgetId: Int, suffix: String) = "widget_${widgetId}_$suffix"
}

object WidgetIconStorage {
    fun iconFile(context: Context, widgetId: Int) =
        context.filesDir.resolve("widget_icons/$widgetId.png")

    fun saveIconFromUri(context: Context, widgetId: Int, sourceUri: Uri): String? {
        return try {
            val directory = context.filesDir.resolve("widget_icons")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val target = iconFile(context, widgetId)
            val sourceBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            } ?: return null
            val iconSizePx = (WidgetAppearance.ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                sourceBitmap,
                iconSizePx,
                iconSizePx,
                true
            )
            val bordered = applyIconBorder(scaled)
            target.outputStream().use { output ->
                bordered.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            if (scaled !== sourceBitmap) {
                scaled.recycle()
            }
            if (bordered !== scaled) {
                bordered.recycle()
            }
            sourceBitmap.recycle()
            fileProviderUri(context, target)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun applyIconBorder(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val output = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(output)
        val strokeWidth = (bitmap.width * 0.08f).coerceAtLeast(2f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        val inset = strokeWidth / 2f
        canvas.drawRoundRect(
            android.graphics.RectF(
                inset,
                inset,
                bitmap.width - inset,
                bitmap.height - inset
            ),
            bitmap.width * 0.18f,
            bitmap.height * 0.18f,
            paint
        )
        return output
    }

    fun deleteIcon(context: Context, widgetId: Int) {
        iconFile(context, widgetId).delete()
    }

    fun iconUri(context: Context, widgetId: Int): Uri? {
        val file = iconFile(context, widgetId)
        return if (file.exists()) fileProviderUri(context, file) else null
    }

    private fun fileProviderUri(context: Context, file: java.io.File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            null
        }
    }
}

object WidgetIconMapper {
    fun drawableRes(iconType: IconType): Int = when (iconType) {
        IconType.PHONE -> R.drawable.ic_widget_phone
        IconType.PERSON -> R.drawable.ic_widget_person
        IconType.HOME -> R.drawable.ic_widget_home
        IconType.CAR -> R.drawable.ic_widget_car
        IconType.WORK -> R.drawable.ic_widget_work
        IconType.STAR -> R.drawable.ic_widget_star
        IconType.HEART -> R.drawable.ic_widget_heart
        IconType.CUSTOM -> R.drawable.ic_widget_phone
    }
}
