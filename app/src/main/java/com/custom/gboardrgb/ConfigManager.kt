package com.custom.gboardrgb

import android.content.Context
import android.content.Intent
import android.graphics.Color
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object ConfigManager {
    const val ACTION_UPDATE_SETTINGS = "com.custom.gboardrgb.UPDATE_SETTINGS"
    const val PERMISSION_SETTINGS_BROADCAST = "com.custom.gboardrgb.permission.SETTINGS_BROADCAST"

    const val EXTRA_EFFECT_ID = "extra_effect_id"
    const val EXTRA_SPEED = "extra_speed"
    const val EXTRA_SIZE = "extra_size"
    const val EXTRA_WAVE_SPEED = "extra_wave_speed"
    const val EXTRA_WAVE_SIZE = "extra_wave_size"
    const val EXTRA_KEY_FLOW_SPEED = "extra_key_flow_speed"
    const val EXTRA_KEY_FLOW_SIZE = "extra_key_flow_size"
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
    private const val KEY_WAVE_SPEED = "key_wave_speed"
    private const val KEY_WAVE_SIZE = "key_wave_size"
    private const val KEY_KEY_FLOW_SPEED = "key_key_flow_speed"
    private const val KEY_KEY_FLOW_SIZE = "key_key_flow_size"
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

    private val rootSyncExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var pendingSyncTask: ScheduledFuture<*>? = null
    private val syncLock = Any()
    @Volatile private var latestJsonSnapshot: String? = null
    @Volatile private var appContextRef: Context? = null

    data class Settings(
        var effectType: EffectType = EffectType.WATER_DROP,
        var speedMultiplier: Float = 1.0f,
        var sizeMultiplier: Float = 1.0f,
        var waveSpeedMultiplier: Float = 1.0f,
        var waveSizeMultiplier: Float = 1.0f,
        var keyFlowSpeedMultiplier: Float = 1.0f,
        var keyFlowSizeMultiplier: Float = 1.0f,
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

    fun sanitize(settings: Settings, previous: Settings? = null): Settings {
        fun clampValue(value: Float, min: Float, max: Float, fallback: Float): Float {
            if (value.isNaN() || value.isInfinite()) {
                return fallback
            }
            return (kotlin.math.round(value * 10f) / 10f).coerceIn(min, max)
        }

        fun isValidColor(colorStr: String?): Boolean {
            if (colorStr.isNullOrBlank()) return false
            return try {
                Color.parseColor(colorStr)
                true
            } catch (_: Exception) {
                false
            }
        }

        val safeEffect = EffectType.fromId(settings.effectType.id)
        val prev = previous ?: Settings()

        val speed = clampValue(settings.speedMultiplier, 0.3f, 2.0f, prev.speedMultiplier)
        val size = clampValue(settings.sizeMultiplier, 0.2f, 1.5f, prev.sizeMultiplier)
        val waveSpeed = clampValue(settings.waveSpeedMultiplier, 0.3f, 2.0f, prev.waveSpeedMultiplier)
        val waveSize = clampValue(settings.waveSizeMultiplier, 0.2f, 1.5f, prev.waveSizeMultiplier)
        val keyFlowSpeed = clampValue(settings.keyFlowSpeedMultiplier, 0.3f, 2.0f, prev.keyFlowSpeedMultiplier)
        val keyFlowSize = clampValue(settings.keyFlowSizeMultiplier, 0.2f, 1.5f, prev.keyFlowSizeMultiplier)

        val primary = if (isValidColor(settings.colorPrimary)) settings.colorPrimary else prev.colorPrimary
        val secondary = if (isValidColor(settings.colorSecondary)) settings.colorSecondary else prev.colorSecondary

        return settings.copy(
            effectType = safeEffect,
            speedMultiplier = speed,
            sizeMultiplier = size,
            waveSpeedMultiplier = waveSpeed,
            waveSizeMultiplier = waveSize,
            keyFlowSpeedMultiplier = keyFlowSpeed,
            keyFlowSizeMultiplier = keyFlowSize,
            colorPrimary = primary,
            colorSecondary = secondary
        )
    }

    fun saveSettings(context: Context, settings: Settings) {
        val cleanSettings = sanitize(settings)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_EFFECT_ID, cleanSettings.effectType.id)
            .putFloat(KEY_SPEED, cleanSettings.speedMultiplier)
            .putFloat(KEY_SIZE, cleanSettings.sizeMultiplier)
            .putFloat(KEY_WAVE_SPEED, cleanSettings.waveSpeedMultiplier)
            .putFloat(KEY_WAVE_SIZE, cleanSettings.waveSizeMultiplier)
            .putFloat(KEY_KEY_FLOW_SPEED, cleanSettings.keyFlowSpeedMultiplier)
            .putFloat(KEY_KEY_FLOW_SIZE, cleanSettings.keyFlowSizeMultiplier)
            .putBoolean(KEY_AMBIENT_RAIN, cleanSettings.isAmbientRainEnabled)
            .putBoolean(KEY_USE_CUSTOM_COLORS, cleanSettings.useCustomColors)
            .putString(KEY_COLOR_PRIMARY, cleanSettings.colorPrimary)
            .putString(KEY_COLOR_SECONDARY, cleanSettings.colorSecondary)
            .putBoolean(KEY_TURBO_DYNAMICS, cleanSettings.isTurboDynamicsEnabled)
            .putBoolean(KEY_GLIDE_TRAIL, cleanSettings.isGlideTrailEnabled)
            .putBoolean(KEY_HAPTIC, cleanSettings.isHapticEnabled)
            .putBoolean(KEY_UNDERGLOW, cleanSettings.isUnderglowEnabled)
            .putBoolean(KEY_VISUAL_EFFECT, cleanSettings.isVisualEffectEnabled)
            .putBoolean(KEY_KEY_SHAPE_FLOW, cleanSettings.isKeyShapeFlowEnabled)
            .putBoolean(KEY_KEY_BORDER_ONLY, cleanSettings.isKeyBorderOnlyEnabled)
            .apply()

        val jsonString = toJson(cleanSettings)

        appContextRef = context.applicationContext
        synchronized(syncLock) {
            latestJsonSnapshot = jsonString
            pendingSyncTask?.cancel(false)
            pendingSyncTask = rootSyncExecutor.schedule({
                val jsonToWrite = latestJsonSnapshot ?: return@schedule
                val ctx = appContextRef ?: return@schedule
                writeRootConfig(ctx, jsonToWrite)
            }, 300, TimeUnit.MILLISECONDS)
        }

        val intent = Intent(ACTION_UPDATE_SETTINGS).apply {
            putExtra(EXTRA_EFFECT_ID, cleanSettings.effectType.id)
            putExtra(EXTRA_SPEED, cleanSettings.speedMultiplier)
            putExtra(EXTRA_SIZE, cleanSettings.sizeMultiplier)
            putExtra(EXTRA_WAVE_SPEED, cleanSettings.waveSpeedMultiplier)
            putExtra(EXTRA_WAVE_SIZE, cleanSettings.waveSizeMultiplier)
            putExtra(EXTRA_KEY_FLOW_SPEED, cleanSettings.keyFlowSpeedMultiplier)
            putExtra(EXTRA_KEY_FLOW_SIZE, cleanSettings.keyFlowSizeMultiplier)
            putExtra(EXTRA_AMBIENT_RAIN, cleanSettings.isAmbientRainEnabled)
            putExtra(EXTRA_USE_CUSTOM_COLORS, cleanSettings.useCustomColors)
            putExtra(EXTRA_COLOR_PRIMARY, cleanSettings.colorPrimary)
            putExtra(EXTRA_COLOR_SECONDARY, cleanSettings.colorSecondary)
            putExtra(EXTRA_TURBO_DYNAMICS, cleanSettings.isTurboDynamicsEnabled)
            putExtra(EXTRA_GLIDE_TRAIL, cleanSettings.isGlideTrailEnabled)
            putExtra(EXTRA_HAPTIC, cleanSettings.isHapticEnabled)
            putExtra(EXTRA_UNDERGLOW, cleanSettings.isUnderglowEnabled)
            putExtra(EXTRA_VISUAL_EFFECT, cleanSettings.isVisualEffectEnabled)
            putExtra(EXTRA_KEY_SHAPE_FLOW, cleanSettings.isKeyShapeFlowEnabled)
            putExtra(EXTRA_KEY_BORDER_ONLY, cleanSettings.isKeyBorderOnlyEnabled)
            `package` = GBOARD_PACKAGE
        }
        context.sendBroadcast(intent)
    }

    fun toJson(settings: Settings): String {
        val clean = sanitize(settings)
        return JSONObject().apply {
            put("effect_id", clean.effectType.id)
            put("speed", clean.speedMultiplier.toDouble())
            put("size", clean.sizeMultiplier.toDouble())
            put("wave_speed", clean.waveSpeedMultiplier.toDouble())
            put("wave_size", clean.waveSizeMultiplier.toDouble())
            put("key_flow_speed", clean.keyFlowSpeedMultiplier.toDouble())
            put("key_flow_size", clean.keyFlowSizeMultiplier.toDouble())
            put("ambient_rain", clean.isAmbientRainEnabled)
            put("use_custom_colors", clean.useCustomColors)
            put("color_primary", clean.colorPrimary)
            put("color_secondary", clean.colorSecondary)
            put("turbo_dynamics", clean.isTurboDynamicsEnabled)
            put("glide_trail", clean.isGlideTrailEnabled)
            put("haptic", clean.isHapticEnabled)
            put("underglow", clean.isUnderglowEnabled)
            put("visual_effect", clean.isVisualEffectEnabled)
            put("key_shape_flow", clean.isKeyShapeFlowEnabled)
            put("key_border_only", clean.isKeyBorderOnlyEnabled)
        }.toString()
    }

    fun fromJson(text: String): Settings? {
        return try {
            val json = JSONObject(text)
            val id = json.optInt("effect_id", 0)
            val legacySpeed = json.optDouble("speed", 1.0).toFloat()
            val legacySize = json.optDouble("size", 1.0).toFloat()
            val waveSpeed = json.optDouble("wave_speed", legacySpeed.toDouble()).toFloat()
            val waveSize = json.optDouble("wave_size", legacySize.toDouble()).toFloat()
            val keyFlowSpeed = json.optDouble("key_flow_speed", legacySpeed.toDouble()).toFloat()
            val keyFlowSize = json.optDouble("key_flow_size", legacySize.toDouble()).toFloat()
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
            val raw = Settings(
                EffectType.fromId(id), waveSpeed, waveSize, waveSpeed, waveSize, keyFlowSpeed, keyFlowSize,
                ambient, useCustom, colPrim, colSec, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
            )
            sanitize(raw)
        } catch (_: Throwable) {
            null
        }
    }

    fun saveLocalGboardConfig(context: Context, settings: Settings) {
        try {
            val jsonString = toJson(settings)
            val targets = mutableListOf<File>()
            try {
                context.filesDir?.let { targets.add(File(it, "gboard_rgb_config.json")) }
            } catch (_: Throwable) {}
            try {
                context.createDeviceProtectedStorageContext()?.filesDir?.let { targets.add(File(it, "gboard_rgb_config.json")) }
            } catch (_: Throwable) {}
            for (file in targets) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(jsonString)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun writeRootConfig(context: Context, jsonContent: String) {
        var tempFile: File? = null
        var scriptFile: File? = null
        try {
            tempFile = File.createTempFile("rgb_cfg_sync_", ".json", context.cacheDir)
            tempFile.writeText(jsonContent)
            tempFile.setReadable(true, false)
            val gboardUid = try {
                context.packageManager.getApplicationInfo(GBOARD_PACKAGE, 0).uid
            } catch (e: Exception) {
                10310
            }
            val syncScript = buildString {
                for (path in CONFIG_FILE_PATHS) {
                    val dir = File(path).parent ?: continue
                    appendLine("mkdir -p '$dir'")
                    appendLine("cp '${tempFile.absolutePath}' '$path'")
                    appendLine("chmod 644 '$path'")
                    appendLine("chown $gboardUid:$gboardUid '$path' 2>/dev/null || true")
                    appendLine("restorecon '$path' 2>/dev/null || true")
                }
            }
            scriptFile = File.createTempFile("sync_cfg_", ".sh", context.cacheDir)
            scriptFile.writeText("#!/system/bin/sh\n$syncScript\n")
            scriptFile.setReadable(true, false)
            scriptFile.setExecutable(true, false)

            val pb = ProcessBuilder("su", "-c", "sh ${scriptFile.absolutePath}")
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
        } catch (e: Exception) {
            // Non-root fallback
        } finally {
            try { tempFile?.delete() } catch (_: Exception) {}
            try { scriptFile?.delete() } catch (_: Exception) {}
        }
    }

    fun loadSettings(context: Context? = null): Settings {
        // 1. If loaded from companion app context, read private SharedPreferences directly
        if (context != null && context.packageName == "com.custom.gboardrgb") {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val id = prefs.getInt(KEY_EFFECT_ID, EffectType.WATER_DROP.id)
                val legacySpeed = prefs.getFloat(KEY_SPEED, 1.0f)
                val legacySize = prefs.getFloat(KEY_SIZE, 1.0f)
                val waveSpeed = prefs.getFloat(KEY_WAVE_SPEED, legacySpeed)
                val waveSize = prefs.getFloat(KEY_WAVE_SIZE, legacySize)
                val keyFlowSpeed = prefs.getFloat(KEY_KEY_FLOW_SPEED, legacySpeed)
                val keyFlowSize = prefs.getFloat(KEY_KEY_FLOW_SIZE, legacySize)
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
                val raw = Settings(
                    EffectType.fromId(id), waveSpeed, waveSize, waveSpeed, waveSize, keyFlowSpeed, keyFlowSize,
                    ambient, useCustom, colPrim, colSec, turbo, glide, haptic, underglow, visualEffect, keyFlow, borderOnly
                )
                return sanitize(raw)
            } catch (e: Throwable) {
                // Fallback to disk
            }
        }

        // 2. In Gboard: Check context's own filesDir and device-protected filesDir first (0ms, direct permission)
        if (context != null) {
            val directFiles = mutableListOf<File>()
            try {
                context.filesDir?.let { directFiles.add(File(it, "gboard_rgb_config.json")) }
            } catch (_: Throwable) {}
            try {
                context.createDeviceProtectedStorageContext()?.filesDir?.let { directFiles.add(File(it, "gboard_rgb_config.json")) }
            } catch (_: Throwable) {}
            for (file in directFiles) {
                try {
                    if (file.exists() && file.canRead()) {
                        val text = file.readText()
                        if (text.isNotBlank()) {
                            val settings = fromJson(text)
                            if (settings != null) return settings
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // 3. Fallback to standard config file paths
        for (path in CONFIG_FILE_PATHS) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        val settings = fromJson(text)
                        if (settings != null) return settings
                    }
                }
            } catch (e: Throwable) {
                // Continue to next path
            }
        }

        // 4. Fallback defaults
        return sanitize(Settings())
    }

    /**
     * Queries SettingsProvider in a background thread to update settings without blocking Gboard's main UI thread.
     */
    fun syncFromProviderAsync(context: Context, onLoaded: (Settings) -> Unit) {
        Thread {
            try {
                val settings = SettingsProvider.getSettings(context)
                if (settings != null) {
                    onLoaded(sanitize(settings))
                }
            } catch (ignored: Throwable) {}
        }.start()
    }
}
