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
    const val EXTRA_UNDERGLOW = "extra_underglow"
    const val EXTRA_VISUAL_EFFECT = "extra_visual_effect"
    const val EXTRA_KEY_SHAPE_FLOW = "extra_key_shape_flow"
    const val EXTRA_KEY_BORDER_ONLY = "extra_key_border_only"

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
    private const val KEY_UNDERGLOW = "key_underglow"
    private const val KEY_VISUAL_EFFECT = "key_visual_effect"
    private const val KEY_KEY_SHAPE_FLOW = "key_key_shape_flow"
    private const val KEY_KEY_BORDER_ONLY = "key_key_border_only"

    private const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
    private val CONFIG_FILE_PATHS = listOf(
        "/data/data/$GBOARD_PACKAGE/files/gboard_rgb_config.json",
        "/data/user_de/0/$GBOARD_PACKAGE/files/gboard_rgb_config.json",
        "/data/local/tmp/gboard_rgb_config.json"
    )

    data class Settings(
        var effectType: EffectType = EffectType.WATER_DROP,
        var speedMultiplier: Float = 1.0f,
        var sizeMultiplier: Float = 1.0f,
        var isAmbientRainEnabled: Boolean = false,
        var useCustomColors: Boolean = false,
        var colorPrimary: String = "#00FFF5",
        var colorSecondary: String = "#FF00AA",
        var isTurboDynamicsEnabled: Boolean = true,
        var isGlideTrailEnabled: Boolean = true,
        var isHapticEnabled: Boolean = true,
        var isUnderglowEnabled: Boolean = false,
        var isVisualEffectEnabled: Boolean = true,
        var isKeyShapeFlowEnabled: Boolean = true,
        var isKeyBorderOnlyEnabled: Boolean = true
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
            .putBoolean(KEY_UNDERGLOW, settings.isUnderglowEnabled)
            .putBoolean(KEY_VISUAL_EFFECT, settings.isVisualEffectEnabled)
            .putBoolean(KEY_KEY_SHAPE_FLOW, settings.isKeyShapeFlowEnabled)
            .putBoolean(KEY_KEY_BORDER_ONLY, settings.isKeyBorderOnlyEnabled)
            .apply()

        val jsonString = JSONObject().apply {
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
            put("underglow", settings.isUnderglowEnabled)
            put("visual_effect", settings.isVisualEffectEnabled)
            put("key_shape_flow", settings.isKeyShapeFlowEnabled)
            put("key_border_only", settings.isKeyBorderOnlyEnabled)
        }.toString()

        // Sync to Gboard files via background root write if possible
        Thread {
            try {
                val tempFile = File(context.cacheDir, "rgb_cfg.json")
                tempFile.writeText(jsonString)
                val gboardUid = try {
                    context.packageManager.getApplicationInfo(GBOARD_PACKAGE, 0).uid
                } catch (e: Exception) {
                    10310
                }
                val syncScript = buildString {
                    for (path in CONFIG_FILE_PATHS) {
                        appendLine("mkdir -p '$(dirname '$path')'")
                        appendLine("cp '${tempFile.absolutePath}' '$path'")
                        appendLine("chmod 644 '$path'")
                        appendLine("chown $gboardUid:$gboardUid '$path'")
                        appendLine("restorecon '$path'")
                    }
                }
                val scriptFile = File.createTempFile("sync_cfg_", ".sh")
                scriptFile.writeText("#!/system/bin/sh\n$syncScript\n")
                Runtime.getRuntime().exec(arrayOf("su", "-c", "sh ${scriptFile.absolutePath}")).waitFor()
                scriptFile.delete()
                tempFile.delete()
            } catch (e: Exception) {
                // Non-root fallback
            }
        }.start()

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
            putExtra(EXTRA_UNDERGLOW, settings.isUnderglowEnabled)
            putExtra(EXTRA_VISUAL_EFFECT, settings.isVisualEffectEnabled)
            putExtra(EXTRA_KEY_SHAPE_FLOW, settings.isKeyShapeFlowEnabled)
            putExtra(EXTRA_KEY_BORDER_ONLY, settings.isKeyBorderOnlyEnabled)
            `package` = GBOARD_PACKAGE
        }
        context.sendBroadcast(intent)
    }

    fun loadSettings(context: Context? = null): Settings {
        if (context != null) {
            // 1. If loaded from our own app context, read private SharedPreferences directly
            if (context.packageName == "com.custom.gboardrgb") {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val id = prefs.getInt(KEY_EFFECT_ID, EffectType.WATER_DROP.id)
                    val speed = (kotlin.math.round(prefs.getFloat(KEY_SPEED, 1.0f) * 10f) / 10f).coerceIn(0.3f, 2.0f)
                    val size = (kotlin.math.round(prefs.getFloat(KEY_SIZE, 1.0f) * 10f) / 10f).coerceIn(0.2f, 1.5f)
                    val ambient = prefs.getBoolean(KEY_AMBIENT_RAIN, false)
                    val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_COLORS, false)
                    val colPrim = prefs.getString(KEY_COLOR_PRIMARY, "#00FFF5") ?: "#00FFF5"
                    val colSec = prefs.getString(KEY_COLOR_SECONDARY, "#FF00AA") ?: "#FF00AA"
                    val turbo = prefs.getBoolean(KEY_TURBO_DYNAMICS, true)
                    val glide = prefs.getBoolean(KEY_GLIDE_TRAIL, true)
                    val haptic = prefs.getBoolean(KEY_HAPTIC, true)
                    val underglow = prefs.getBoolean(KEY_UNDERGLOW, false)
                    val visualEffect = prefs.getBoolean(KEY_VISUAL_EFFECT, true)
                    val keyFlow = prefs.getBoolean(KEY_KEY_SHAPE_FLOW, true)
                    val borderOnly = prefs.getBoolean(KEY_KEY_BORDER_ONLY, true)
                    return Settings(
                        EffectType.fromId(id), speed, size, ambient,
                        useCustom, colPrim, colSec, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
                    )
                } catch (e: Exception) {
                    // Fallback
                }
            } else {
                // 2. If loaded from Gboard context, query SettingsProvider
                try {
                    val fromProvider = SettingsProvider.getSettings(context)
                    return fromProvider
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }

        // 3. Fallback to reading disk JSON files
        for (path in CONFIG_FILE_PATHS) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    val json = JSONObject(text)
                    val id = json.optInt("effect_id", 0)
                    val speed = json.optDouble("speed", 1.0).toFloat()
                    val size = json.optDouble("size", 1.0).toFloat()
                    val ambient = json.optBoolean("ambient_rain", false)
                    val useCustom = json.optBoolean("use_custom_colors", false)
                    val colPrim = json.optString("color_primary", "#00FFF5")
                    val colSec = json.optString("color_secondary", "#FF00AA")
                    val turbo = json.optBoolean("turbo_dynamics", true)
                    val glide = json.optBoolean("glide_trail", true)
                    val haptic = json.optBoolean("haptic", true)
                    val underglow = json.optBoolean("underglow", false)
                    val visualEffect = json.optBoolean("visual_effect", true)
                    val keyFlow = json.optBoolean("key_shape_flow", true)
                    val borderOnly = json.optBoolean("key_border_only", true)
                    return Settings(
                        EffectType.fromId(id), speed, size, ambient,
                        useCustom, colPrim, colSec, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
                    )
                }
            } catch (e: Exception) {
                // Continue to next path
            }
        }

        return Settings()
    }
}
