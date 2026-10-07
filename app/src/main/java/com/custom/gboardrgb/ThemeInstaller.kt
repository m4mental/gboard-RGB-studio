package com.custom.gboardrgb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object ThemeInstaller {

    private const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
    private val GBOARD_PREFS_FILES = listOf(
        "/data/user_de/0/$GBOARD_PACKAGE/shared_prefs/com.google.android.inputmethod.latin_preferences.xml",
        "/data/data/$GBOARD_PACKAGE/shared_prefs/com.google.android.inputmethod.latin_preferences.xml"
    )

    data class RootResult(val exitCode: Int, val output: String)

    private fun runRootScript(script: String): RootResult {
        val scriptFile = File.createTempFile("root_script_", ".sh")
        return try {
            scriptFile.writeText("#!/system/bin/sh\n$script\n")
            scriptFile.setReadable(true, false)
            scriptFile.setExecutable(true, false)

            val pb = ProcessBuilder("su", "-c", "sh ${scriptFile.absolutePath}")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            RootResult(exitCode, output.trim())
        } catch (e: Exception) {
            throw IOException("Root access unavailable or command execution failed: ${e.message}", e)
        } finally {
            scriptFile.delete()
        }
    }

    suspend fun restoreDefaultTheme(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if Gboard is installed; do not use UID fallback
            val gboardUid = try {
                context.packageManager.getApplicationInfo(GBOARD_PACKAGE, 0).uid
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Gboard ($GBOARD_PACKAGE) is not installed."))
            }

            // Verify root is available
            val suCheck = try {
                runRootScript("id")
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalStateException("Root request denied or su binary missing: ${e.message}"))
            }
            if (suCheck.exitCode != 0) {
                return@withContext Result.failure(IllegalStateException("Root permission denied (exit code ${suCheck.exitCode}): ${suCheck.output}"))
            }

            // Force stop Gboard
            val stop1 = runRootScript("am force-stop $GBOARD_PACKAGE")
            if (stop1.exitCode != 0) {
                return@withContext Result.failure(IllegalStateException("Failed to force-stop Gboard (exit code ${stop1.exitCode}): ${stop1.output}"))
            }

            for (prefFile in GBOARD_PREFS_FILES) {
                val checkResult = runRootScript("if [ -e '$prefFile' ]; then echo EXISTS; else echo ABSENT; fi")
                if (checkResult.exitCode != 0) {
                    return@withContext Result.failure(IllegalStateException("Failed to check prefs file '$prefFile' (exit code ${checkResult.exitCode}): ${checkResult.output}"))
                }
                if (checkResult.output.trim() == "ABSENT") {
                    continue
                }

                val catResult = runRootScript("cat '$prefFile'")
                if (catResult.exitCode != 0) {
                    return@withContext Result.failure(IllegalStateException("Failed to read prefs file '$prefFile' (exit code ${catResult.exitCode}): ${catResult.output}"))
                }
                val xmlContent = catResult.output
                if (xmlContent.contains("additional_keyboard_theme")) {
                    val regex = Regex("\\s*<string name=\"additional_keyboard_theme\">.*?</string>", RegexOption.DOT_MATCHES_ALL)
                    val cleanedXml = xmlContent.replace(regex, "")

                    val tempFile = File.createTempFile("prefs_reset_", ".xml", context.cacheDir)
                    tempFile.writeText(cleanedXml.trim())

                    val backupPath = "$prefFile.bak"
                    val rollbackCmd = "{ if cp '$backupPath' '$prefFile'; then rm -f '$backupPath'; else echo \"Rollback failed. Backup retained at '$backupPath'\"; fi; exit"
                    val script = buildString {
                        appendLine("cp '$prefFile' '$backupPath' || exit 1")
                        appendLine("cp '${tempFile.absolutePath}' '$prefFile' || $rollbackCmd 2; }")
                        appendLine("chmod 660 '$prefFile' || $rollbackCmd 3; }")
                        appendLine("chown $gboardUid:$gboardUid '$prefFile' || $rollbackCmd 4; }")
                        appendLine("restorecon '$prefFile' || $rollbackCmd 5; }")
                        appendLine("rm -f '$backupPath'")
                    }

                    val applyResult = try {
                        runRootScript(script)
                    } catch (e: Exception) {
                        tempFile.delete()
                        return@withContext Result.failure(IllegalStateException("Failed to write prefs file '$prefFile': ${e.message}"))
                    } finally {
                        tempFile.delete()
                    }

                    if (applyResult.exitCode != 0) {
                        return@withContext Result.failure(IllegalStateException("Failed to update prefs file '$prefFile' (exit code ${applyResult.exitCode}): ${applyResult.output}"))
                    }
                }
            }

            // Force stop Gboard again to reload prefs
            val stop2 = runRootScript("am force-stop $GBOARD_PACKAGE")
            if (stop2.exitCode != 0) {
                return@withContext Result.failure(IllegalStateException("Failed to restart Gboard (exit code ${stop2.exitCode}): ${stop2.output}"))
            }

            ConfigManager.saveSettings(context, ConfigManager.loadSettings(context))
            Result.success("Stock Gboard theme restored.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
