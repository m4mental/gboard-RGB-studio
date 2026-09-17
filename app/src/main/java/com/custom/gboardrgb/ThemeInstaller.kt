package com.custom.gboardrgb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThemeInstaller {

    private const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
    private val GBOARD_THEMES_DIRS = listOf(
        "/data/data/$GBOARD_PACKAGE/files/themes",
        "/data/user_de/0/$GBOARD_PACKAGE/files/themes"
    )
    private val GBOARD_PREFS_FILES = listOf(
        "/data/user_de/0/$GBOARD_PACKAGE/shared_prefs/com.google.android.inputmethod.latin_preferences.xml",
        "/data/data/$GBOARD_PACKAGE/shared_prefs/com.google.android.inputmethod.latin_preferences.xml"
    )

    suspend fun applyTheme(
        context: Context,
        assetFileName: String,
        withBorders: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Copy asset file to local cache
            val cacheFile = File(context.cacheDir, assetFileName)
            context.assets.open(assetFileName).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            val themePrefValue = "files:themes/$assetFileName"

            val gboardUid = try {
                context.packageManager.getApplicationInfo(GBOARD_PACKAGE, 0).uid
            } catch (e: Exception) {
                10310
            }

            // 2. Force-stop Gboard first so in-memory cache doesn't overwrite prefs
            runRootScript("am force-stop $GBOARD_PACKAGE")

            // 3. Deploy theme file to both locations
            val deployDirsScript = buildString {
                for (dir in GBOARD_THEMES_DIRS) {
                    appendLine("mkdir -p '$dir'")
                    appendLine("cp '${cacheFile.absolutePath}' '$dir/$assetFileName'")
                    appendLine("chmod 755 '$dir'")
                    appendLine("chmod 644 '$dir/$assetFileName'")
                    appendLine("chown -R $gboardUid:$gboardUid '$dir'")
                    appendLine("restorecon -R '$dir'")
                }
            }
            runRootScript(deployDirsScript)

            // 4. Update both preferences files
            for (prefFile in GBOARD_PREFS_FILES) {
                var xmlContent = runRootScript("cat '$prefFile' 2>/dev/null")
                if (!xmlContent.contains("<map>")) {
                    xmlContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>"
                }

                // Update additional_keyboard_theme
                xmlContent = if (xmlContent.contains("name=\"additional_keyboard_theme\"")) {
                    xmlContent.replace(
                        Regex("<string name=\"additional_keyboard_theme\">.*?</string>"),
                        "<string name=\"additional_keyboard_theme\">$themePrefValue</string>"
                    )
                } else {
                    xmlContent.replace(
                        "<map>",
                        "<map>\n    <string name=\"additional_keyboard_theme\">$themePrefValue</string>"
                    )
                }

                // Update enable_key_border
                xmlContent = if (xmlContent.contains("name=\"enable_key_border\"")) {
                    xmlContent.replace(
                        Regex("<boolean name=\"enable_key_border\" value=\".*?\" />"),
                        "<boolean name=\"enable_key_border\" value=\"$withBorders\" />"
                    )
                } else {
                    xmlContent.replace(
                        "<map>",
                        "<map>\n    <boolean name=\"enable_key_border\" value=\"$withBorders\" />"
                    )
                }

                val tempPref = File(context.cacheDir, "temp_pref_${File(prefFile).parentFile?.parentFile?.name}.xml")
                tempPref.writeText(xmlContent.trim())

                val writeScript = buildString {
                    appendLine("mkdir -p '$(dirname '$prefFile')'")
                    appendLine("cp '${tempPref.absolutePath}' '$prefFile'")
                    appendLine("chmod 660 '$prefFile'")
                    appendLine("chown $gboardUid:$gboardUid '$prefFile'")
                    appendLine("restorecon '$prefFile'")
                }
                runRootScript(writeScript)
                tempPref.delete()
            }

            // 5. Force-stop Gboard again to reload new preferences cleanly
            runRootScript("am force-stop $GBOARD_PACKAGE")

            // 6. Re-sync user RGB settings so Gboard never reverts toggles
            ConfigManager.saveSettings(context, ConfigManager.loadSettings(context))

            val themeName = if (assetFileName.contains("Black", ignoreCase = true)) "3D Black" else "3D White"
            Result.success("$themeName Theme applied successfully! Open Gboard to experience it.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreDefaultTheme(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val gboardUid = try {
                context.packageManager.getApplicationInfo(GBOARD_PACKAGE, 0).uid
            } catch (e: Exception) {
                10310
            }

            runRootScript("am force-stop $GBOARD_PACKAGE")

            for (prefFile in GBOARD_PREFS_FILES) {
                val xmlContent = runRootScript("cat '$prefFile' 2>/dev/null")
                if (xmlContent.contains("additional_keyboard_theme")) {
                    val cleanedXml = xmlContent.replace(
                        Regex("\\s*<string name=\"additional_keyboard_theme\">.*?</string>"),
                        ""
                    )
                    val tempFile = File.createTempFile("prefs_reset", ".xml")
                    tempFile.writeText(cleanedXml.trim())

                    val script = buildString {
                        appendLine("cp '${tempFile.absolutePath}' '$prefFile'")
                        appendLine("chmod 660 '$prefFile'")
                        appendLine("chown $gboardUid:$gboardUid '$prefFile'")
                        appendLine("restorecon '$prefFile'")
                    }
                    runRootScript(script)
                    tempFile.delete()
                }
            }

            runRootScript("am force-stop $GBOARD_PACKAGE")
            ConfigManager.saveSettings(context, ConfigManager.loadSettings(context))
            Result.success("Stock Gboard theme restored.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveThemeName(): String = withContext(Dispatchers.IO) {
        try {
            for (prefFile in GBOARD_PREFS_FILES) {
                val out = runRootScript("cat '$prefFile' 2>/dev/null")
                val match = Regex("<string name=\"additional_keyboard_theme\">(.*?)</string>").find(out)
                val raw = match?.groupValues?.getOrNull(1)
                if (!raw.isNullOrBlank()) {
                    return@withContext when {
                        raw.contains("3D_Black", ignoreCase = true) -> "3D Black Edition"
                        raw.contains("3D_White", ignoreCase = true) -> "3D White Edition"
                        raw.contains("system_auto", ignoreCase = true) -> "System Default (Auto)"
                        else -> raw.substringAfterLast("/")
                    }
                }
            }
            "System Default"
        } catch (e: Exception) {
            "System Default"
        }
    }

    private fun runRootScript(script: String): String {
        return try {
            val scriptFile = File.createTempFile("root_script_", ".sh")
            scriptFile.writeText("#!/system/bin/sh\n$script\n")
            scriptFile.setReadable(true, false)
            scriptFile.setExecutable(true, false)

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh ${scriptFile.absolutePath}"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            scriptFile.delete()
            output
        } catch (e: Exception) {
            ""
        }
    }
}
