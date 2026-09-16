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

    private const val PREFS_NAME = "gboard_rgb_prefs"
    private const val KEY_EFFECT_ID = "key_effect_id"
    private const val KEY_SPEED = "key_speed"
    private const val KEY_SIZE = "key_size"
    private const val KEY_AMBIENT_RAIN = "key_ambient_rain"

    private const val FALLBACK_FILE_PATH = "/data/local/tmp/gboard_rgb_config.json"

    data class Settings(
        var effectType: EffectType = EffectType.WATER_DROP,
        var speedMultiplier: Float = 1.0f,
        var sizeMultiplier: Float = 1.0f,
        var isAmbientRainEnabled: Boolean = true
    )

    fun saveSettings(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_EFFECT_ID, settings.effectType.id)
            .putFloat(KEY_SPEED, settings.speedMultiplier)
            .putFloat(KEY_SIZE, settings.sizeMultiplier)
            .putBoolean(KEY_AMBIENT_RAIN, settings.isAmbientRainEnabled)
            .apply()

        try {
            val json = JSONObject().apply {
                put("effect_id", settings.effectType.id)
                put("speed", settings.speedMultiplier.toDouble())
                put("size", settings.sizeMultiplier.toDouble())
                put("ambient_rain", settings.isAmbientRainEnabled)
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
                return Settings(EffectType.fromId(id), speed, size, ambient)
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
                return Settings(EffectType.fromId(id), speed, size, ambient)
            }
        } catch (e: Exception) {
            // Ignore
        }

        return Settings()
    }
}
