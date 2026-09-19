/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lockscreen

import android.content.Context
import android.os.UserHandle
import android.provider.Settings

data class ClockCustomizerState(
    val style: Int = 0,
    val initialStyle: Int = 0,
    val scale: Int = 100,
    val opacity: Int = 100,
    val marginTop: Int = 15,
    val marginStart: Int = 0,
    val colorMode: String = COLOR_MODE_ACCENT,
    val customColor: Int = DEFAULT_CUSTOM_COLOR,
    val gradientEnabled: Boolean = false,
    val gradientColorStart: Int = DEFAULT_GRADIENT_START,
    val gradientColorEnd: Int = DEFAULT_GRADIENT_END,
    val gradientAnchorY: Int = 50,
    val gradientRadius: Int = 100,
    val albumArtColor: Boolean = false,
    val aodAnim: Boolean = true,
    val wobbleOnCharge: Boolean = true,
    val weatherEnabled: Boolean = true
) {
    companion object {
        const val COLOR_MODE_DEFAULT = "default"
        const val COLOR_MODE_ACCENT = "accent"
        const val COLOR_MODE_CUSTOM = "custom"
        const val COLOR_MODE_GRADIENT = "gradient"

        const val DEFAULT_CUSTOM_COLOR = -0x1 // 0xFFFFFFFF
        const val DEFAULT_GRADIENT_START = -0xFF1A01 // 0xFF00E5FF
        const val DEFAULT_GRADIENT_END = -0xD256 // 0xFFFF2DAA

        val NO_COLOR_CLOCKS = setOf(1, 2, 25, 26)

        fun isNoColorClock(style: Int): Boolean = NO_COLOR_CLOCKS.contains(style)

        fun load(context: Context): ClockCustomizerState {
            val cr = context.contentResolver
            val style = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, 0, UserHandle.USER_CURRENT
            )
            val scale = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_SIZE, 100, UserHandle.USER_CURRENT
            )
            val opacity = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_OPACITY, 100, UserHandle.USER_CURRENT
            )
            val marginTop = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_TOP, 15, UserHandle.USER_CURRENT
            )
            val marginStart = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_START, 0, UserHandle.USER_CURRENT
            )

            val gradientEnabled = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ENABLED, 0, UserHandle.USER_CURRENT
            ) == 1

            val rawColorMode = Settings.Secure.getStringForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_COLOR_MODE, UserHandle.USER_CURRENT
            ) ?: COLOR_MODE_ACCENT

            val colorMode = if (gradientEnabled) COLOR_MODE_GRADIENT else rawColorMode

            val customColor = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR, UserHandle.USER_CURRENT
            )
            val gradientColorStart = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_START, DEFAULT_GRADIENT_START, UserHandle.USER_CURRENT
            )
            val gradientColorEnd = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_END, DEFAULT_GRADIENT_END, UserHandle.USER_CURRENT
            )
            val gradientAnchorY = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ANCHOR_Y, 50, UserHandle.USER_CURRENT
            )
            val gradientRadius = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_RADIUS, 100, UserHandle.USER_CURRENT
            )

            val albumArtColor = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_ALBUM_ART_COLOR, 0, UserHandle.USER_CURRENT
            ) == 1
            val aodAnim = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_AOD_ANIM, 1, UserHandle.USER_CURRENT
            ) == 1
            val wobbleOnCharge = Settings.Secure.getIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_WOBBLE_ON_CHARGE, 1, UserHandle.USER_CURRENT
            ) == 1
            val weatherEnabled = Settings.Secure.getIntForUser(
                cr, Settings.Secure.CUSTOM_CLOCK_WEATHER, 1, UserHandle.USER_CURRENT
            ) == 1

            return ClockCustomizerState(
                style = style,
                initialStyle = style,
                scale = scale,
                opacity = opacity,
                marginTop = marginTop,
                marginStart = marginStart,
                colorMode = colorMode,
                customColor = customColor,
                gradientEnabled = gradientEnabled,
                gradientColorStart = gradientColorStart,
                gradientColorEnd = gradientColorEnd,
                gradientAnchorY = gradientAnchorY,
                gradientRadius = gradientRadius,
                albumArtColor = albumArtColor,
                aodAnim = aodAnim,
                wobbleOnCharge = wobbleOnCharge,
                weatherEnabled = weatherEnabled
            )
        }

        fun save(context: Context, state: ClockCustomizerState) {
            val cr = context.contentResolver

            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE, state.style, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE, 0, UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                cr, Settings.System.LS_CLOCK_HIDE, if (state.style != 0) 1 else 0, UserHandle.USER_CURRENT
            )

            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_SIZE, state.scale, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_OPACITY, state.opacity, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_TOP, state.marginTop, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_MARGIN_START, state.marginStart, UserHandle.USER_CURRENT
            )

            val effectiveColorMode = if (state.colorMode == COLOR_MODE_GRADIENT) {
                COLOR_MODE_CUSTOM
            } else {
                state.colorMode
            }

            Settings.Secure.putStringForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_COLOR_MODE, effectiveColorMode, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_CUSTOM_COLOR, state.customColor, UserHandle.USER_CURRENT
            )

            val isGrad = state.gradientEnabled || state.colorMode == COLOR_MODE_GRADIENT
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ENABLED,
                if (isGrad) 1 else 0,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_START, state.gradientColorStart, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_COLOR_END, state.gradientColorEnd, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_ANCHOR_Y, state.gradientAnchorY, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_GRADIENT_RADIUS, state.gradientRadius, UserHandle.USER_CURRENT
            )

            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_ALBUM_ART_COLOR,
                if (state.albumArtColor) 1 else 0, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_AOD_ANIM,
                if (state.aodAnim) 1 else 0, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_WOBBLE_ON_CHARGE,
                if (state.wobbleOnCharge) 1 else 0, UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                cr, Settings.Secure.CUSTOM_CLOCK_WEATHER,
                if (state.weatherEnabled) 1 else 0, UserHandle.USER_CURRENT
            )
        }

        fun applyAndRestart(context: Context, state: ClockCustomizerState) {
            save(context, state)
            SystemUtilsNew.restartSystemUI(context)
        }
    }
}
