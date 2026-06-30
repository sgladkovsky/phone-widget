package com.flymeauto.phonewidget

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider

data class WidgetConfig(
    val contactName: String,
    val phoneNumber: String,
    val iconType: IconType,
    val customIconUri: String?
) {
    val displayName: String
        get() = contactName.ifBlank { phoneNumber }

    companion object {
        fun empty() = WidgetConfig("", "", IconType.PHONE, null)
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
            customIconUri = preferences.getString(key(widgetId, "icon_uri"), null)
        )
    }

    fun delete(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(key(widgetId, "name"))
            .remove(key(widgetId, "phone"))
            .remove(key(widgetId, "icon_type"))
            .remove(key(widgetId, "icon_uri"))
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
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            fileProviderUri(context, target)?.toString()
        } catch (_: Exception) {
            null
        }
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
