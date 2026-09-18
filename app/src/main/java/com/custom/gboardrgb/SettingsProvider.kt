package com.custom.gboardrgb

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.custom.gboardrgb.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/settings")
        const val METHOD_GET_SETTINGS = "getSettings"

        fun getSettings(context: Context): ConfigManager.Settings? {
            return try {
                val bundle = context.contentResolver.call(
                    CONTENT_URI,
                    METHOD_GET_SETTINGS,
                    null,
                    null
                )
                if (bundle != null) {
                    bundleToSettings(bundle)
                } else {
                    null
                }
            } catch (e: Throwable) {
                null
            }
        }

        fun bundleToSettings(bundle: Bundle): ConfigManager.Settings {
            val id = bundle.getInt(ConfigManager.EXTRA_EFFECT_ID, EffectType.WATER_DROP.id)
            val speed = bundle.getFloat(ConfigManager.EXTRA_SPEED, 1.0f)
            val size = bundle.getFloat(ConfigManager.EXTRA_SIZE, 1.0f)
            val ambient = bundle.getBoolean(ConfigManager.EXTRA_AMBIENT_RAIN, false)
            val useCustom = bundle.getBoolean(ConfigManager.EXTRA_USE_CUSTOM_COLORS, false)
            val colPrim = bundle.getString(ConfigManager.EXTRA_COLOR_PRIMARY, "#00FFF5") ?: "#00FFF5"
            val colSec = bundle.getString(ConfigManager.EXTRA_COLOR_SECONDARY, "#FF00AA") ?: "#FF00AA"
            val turbo = bundle.getBoolean(ConfigManager.EXTRA_TURBO_DYNAMICS, true)
            val glide = bundle.getBoolean(ConfigManager.EXTRA_GLIDE_TRAIL, true)
            val haptic = bundle.getBoolean(ConfigManager.EXTRA_HAPTIC, true)
            val underglow = bundle.getBoolean(ConfigManager.EXTRA_UNDERGLOW, false)
            val visualEffect = bundle.getBoolean(ConfigManager.EXTRA_VISUAL_EFFECT, true)
            val keyFlow = bundle.getBoolean(ConfigManager.EXTRA_KEY_SHAPE_FLOW, true)
            val borderOnly = bundle.getBoolean(ConfigManager.EXTRA_KEY_BORDER_ONLY, true)
            return ConfigManager.Settings(
                EffectType.fromId(id), speed, size, ambient,
                useCustom, colPrim, colSec, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
            )
        }

        fun settingsToBundle(settings: ConfigManager.Settings): Bundle {
            return Bundle().apply {
                putInt(ConfigManager.EXTRA_EFFECT_ID, settings.effectType.id)
                putFloat(ConfigManager.EXTRA_SPEED, settings.speedMultiplier)
                putFloat(ConfigManager.EXTRA_SIZE, settings.sizeMultiplier)
                putBoolean(ConfigManager.EXTRA_AMBIENT_RAIN, settings.isAmbientRainEnabled)
                putBoolean(ConfigManager.EXTRA_USE_CUSTOM_COLORS, settings.useCustomColors)
                putString(ConfigManager.EXTRA_COLOR_PRIMARY, settings.colorPrimary)
                putString(ConfigManager.EXTRA_COLOR_SECONDARY, settings.colorSecondary)
                putBoolean(ConfigManager.EXTRA_TURBO_DYNAMICS, settings.isTurboDynamicsEnabled)
                putBoolean(ConfigManager.EXTRA_GLIDE_TRAIL, settings.isGlideTrailEnabled)
                putBoolean(ConfigManager.EXTRA_HAPTIC, settings.isHapticEnabled)
                putBoolean(ConfigManager.EXTRA_UNDERGLOW, settings.isUnderglowEnabled)
                putBoolean(ConfigManager.EXTRA_VISUAL_EFFECT, settings.isVisualEffectEnabled)
                putBoolean(ConfigManager.EXTRA_KEY_SHAPE_FLOW, settings.isKeyShapeFlowEnabled)
                putBoolean(ConfigManager.EXTRA_KEY_BORDER_ONLY, settings.isKeyBorderOnlyEnabled)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_GET_SETTINGS) {
            val ctx = context ?: return null
            val settings = ConfigManager.loadSettings(ctx)
            return settingsToBundle(settings)
        }
        return super.call(method, arg, extras)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
