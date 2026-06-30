package com.flymeauto.phonewidget

import android.graphics.Color

object WidgetAppearance {
    const val DEFAULT_TRANSPARENCY_PERCENT = 30
    const val ICON_SIZE_DP = 48

    fun backgroundColor(transparencyPercent: Int): Int {
        val transparency = transparencyPercent.coerceIn(0, 100)
        val alpha = ((100 - transparency) * 255) / 100
        return Color.argb(alpha, 0x1E, 0x2A, 0x3A)
    }

    fun opacity(transparencyPercent: Int): Float {
        val transparency = transparencyPercent.coerceIn(0, 100)
        return (100 - transparency) / 100f
    }
}
