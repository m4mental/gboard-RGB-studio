package com.custom.gboardrgb

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

object ConfigManager {
    const val ACTION_UPDATE_SETTINGS = "com.custom.gboardrgb.UPDATE_SETTINGS"
    const val EXTRA_EFFECT_ID = "extra_effect_id"
    const val EXTRA_SPEED = "extra_speed"
    const val EXTRA_SIZE = "extra_size"
    const val EXTRA_AMBIENT_RAIN = "extra_ambient_rain"
    const val EXTRA_USE_CUSTOM_COLORS = "extra_use_custom_colors"
    const val EXTRA_COLOR_PRIMARY = "extra_color_primary"
    const val EXTRA_COLOR_SECONDARY = "extra_color_secondary"
    const val EXTRA_TURBO_DYNAMICS = "extra_turbo_dynamics"
    const val EXTRA_GLIDE_TRAIL = "extra_glide_trail"
    const val EXTRA_HAPTIC = "extra_haptic"

    private const val PREFS_NAME = "gboard_rgb_prefs"
    private const val KEY_EFFECT_ID = "key_effect_id"
    private const val KEY_SPEED = "key_speed"
    private const val KEY_SIZE = "key_size"
    private const val KEY_AMBIENT_RAIN = "key_ambient_rain"
    private const val KEY_USE_CUSTOM_COLORS = "key_use_custom_colors"
    private const val KEY_COLOR_PRIMARY = "key_color_primary"
    private const val KEY_COLOR_SECONDARY = "key_color_secondary"
    private const val KEY_TURBO_DYNAMICS = "key_turbo_dynamics"
    private const val KEY_GLIDE_TRAIL = "key_glide_trail"
    private const val KEY_HAPTIC = "key_haptic"

    private const val FALLBACK_FILE_PATH = "/data/local/tmp/gboard_rgb_config.json"

    data class Settings(
        var effectType: EffectType = EffectType.WATER_DROP,
        var speedMultiplier: Float = 1.0f,
        var sizeMultiplier: Float = 1.0f,
        var isAmbientRainEnabled: Boolean = true,
        var useCustomColors: Boolean = false,
        var colorPrimary: String = "#00FFF5",
        var colorSecondary: String = "#FF00AA",
        var isTurboDynamicsEnabled: Boolean = true,
        var isGlideTrailEnabled: Boolean = true,
        var isHapticEnabled: Boolean = true
    )

    fun saveSettings(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_EFFECT_ID, settings.effectType.id)
            .putFloat(KEY_SPEED, settings.speedMultiplier)
            .putFloat(KEY_SIZE, settings.sizeMultiplier)
            .putBoolean(KEY_AMBIENT_RAIN, settings.isAmbientRainEnabled)
            .putBoolean(KEY_USE_CUSTOM_COLORS, settings.useCustomColors)
            .putString(KEY_COLOR_PRIMARY, settings.colorPrimary)
            .putString(KEY_COLOR_SECONDARY, settings.colorSecondary)
            .putBoolean(KEY_TURBO_DYNAMICS, settings.isTurboDynamicsEnabled)
            .putBoolean(KEY_GLIDE_TRAIL, settings.isGlideTrailEnabled)
            .putBoolean(KEY_HAPTIC, settings.isHapticEnabled)
            .apply()

        try {
            val json = JSONObject().apply {
                put("effect_id", settings.effectType.id)
                put("speed", settings.speedMultiplier.toDouble())
                put("size", settings.sizeMultiplier.toDouble())
                put("ambient_rain", settings.isAmbientRainEnabled)
                put("use_custom_colors", settings.useCustomColors)
                put("color_primary", settings.colorPrimary)
                put("color_secondary", settings.colorSecondary)
                put("turbo_dynamics", settings.isTurboDynamicsEnabled)
                put("glide_trail", settings.isGlideTrailEnabled)
                put("haptic", settings.isHapticEnabled)
            }
            val file = File(FALLBACK_FILE_PATH)
            file.writeText(json.toString())
            file.setReadable(true, false)
            file.setWritable(true, false)
        } catch (e: Exception) {
            // Fallback
        }

        val intent = Intent(ACTION_UPDATE_SETTINGS).apply {
            putExtra(EXTRA_EFFECT_ID, settings.effectType.id)
            putExtra(EXTRA_SPEED, settings.speedMultiplier)
            putExtra(EXTRA_SIZE, settings.sizeMultiplier)
            putExtra(EXTRA_AMBIENT_RAIN, settings.isAmbientRainEnabled)
            putExtra(EXTRA_USE_CUSTOM_COLORS, settings.useCustomColors)
            putExtra(EXTRA_COLOR_PRIMARY, settings.colorPrimary)
            putExtra(EXTRA_COLOR_SECONDARY, settings.colorSecondary)
            putExtra(EXTRA_TURBO_DYNAMICS, settings.isTurboDynamicsEnabled)
            putExtra(EXTRA_GLIDE_TRAIL, settings.isGlideTrailEnabled)
            putExtra(EXTRA_HAPTIC, settings.isHapticEnabled)
            `package` = "com.google.android.inputmethod.latin"
        }
        context.sendBroadcast(intent)
    }

    fun loadSettings(context: Context? = null): Settings {
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val id = prefs.getInt(KEY_EFFECT_ID, EffectType.WATER_DROP.id)
                val speed = prefs.getFloat(KEY_SPEED, 1.0f)
                val size = prefs.getFloat(KEY_SIZE, 1.0f)
                val ambient = prefs.getBoolean(KEY_AMBIENT_RAIN, true)
                val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_COLORS, false)
                val colPrim = prefs.getString(KEY_COLOR_PRIMARY, "#00FFF5") ?: "#00FFF5"
                val colSec = prefs.getString(KEY_COLOR_SECONDARY, "#FF00AA") ?: "#FF00AA"
                val turbo = prefs.getBoolean(KEY_TURBO_DYNAMICS, true)
                val glide = prefs.getBoolean(KEY_GLIDE_TRAIL, true)
                val haptic = prefs.getBoolean(KEY_HAPTIC, true)
                return Settings(
                    EffectType.fromId(id), speed, size, ambient,
                    useCustom, colPrim, colSec, turbo, glide, haptic
                )
            } catch (e: Exception) {
                // Fallback
            }
        }

        try {
            val file = File(FALLBACK_FILE_PATH)
            if (file.exists()) {
                val text = file.readText()
                val json = JSONObject(text)
                val id = json.optInt("effect_id", 0)
                val speed = json.optDouble("speed", 1.0).toFloat()
                val size = json.optDouble("size", 1.0).toFloat()
                val ambient = json.optBoolean("ambient_rain", true)
                val useCustom = json.optBoolean("use_custom_colors", false)
                val colPrim = json.optString("color_primary", "#00FFF5")
                val colSec = json.optString("color_secondary", "#FF00AA")
                val turbo = json.optBoolean("turbo_dynamics", true)
                val glide = json.optBoolean("glide_trail", true)
                val haptic = json.optBoolean("haptic", true)
                return Settings(
                    EffectType.fromId(id), speed, size, ambient,
                    useCustom, colPrim, colSec, turbo, glide, haptic
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        return Settings()
    }
}
