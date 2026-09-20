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
