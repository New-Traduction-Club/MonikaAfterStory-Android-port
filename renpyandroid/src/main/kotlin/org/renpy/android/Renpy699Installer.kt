package org.renpy.android

import android.content.Context
import android.util.Log
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

object Renpy699Installer {

    private const val TAG = "Renpy699Installer"
    private const val VERSION = "6.99"

    fun isInstalled(gameDir: File): Boolean {
        val versionFile = File(gameDir, ".runtime_699.version")
        val mainPy = File(gameDir, "main.py")
        if (!versionFile.exists() || !mainPy.exists()) return false
        return try {
            versionFile.readText().trim() == VERSION
        } catch (e: Exception) {
            false
        }
    }

    fun ensureInstalled(context: Context, gameDir: File): Boolean {
        if (isInstalled(gameDir)) {
            Log.i(TAG, "Ren'Py 6.99 runtime already installed in ${gameDir.name}")
            return true
        }

        Log.i(TAG, "Installing Ren'Py 6.99 runtime in ${gameDir.name}...")

        try {
            cleanOldEngineFiles(gameDir)

            val privateOk = extractTarGz(context, "private.mp3", gameDir)
            if (!privateOk) {
                Log.e(TAG, "Failed to extract private.mp3")
                return false
            }

            cleanForeignBinaries(gameDir)

            val resId = context.resources.getIdentifier("private_version", "string", context.packageName)
            val privateVersion = if (resId != 0) {
                try {
                    context.getString(resId)
                } catch (e: Exception) {
                    "1797885c5794ccae80ef4ea500ed1e6e"
                }
            } else {
                "1797885c5794ccae80ef4ea500ed1e6e"
            }

            File(gameDir, ".runtime_699.version").writeText(VERSION)
            File(gameDir, "private.version").writeText(privateVersion)
            File(gameDir, ".private.version").writeText(privateVersion)
            File(gameDir, ".nomedia").createNewFile()

            Log.i(TAG, "Ren'Py 6.99 runtime successfully installed in ${gameDir.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing Ren'Py 6.99 runtime", e)
            return false
        }
    }

    internal fun cleanOldEngineFiles(gameDir: File) {
        File(gameDir, "main.pyo").delete()
        File(gameDir, "main.pyc").delete()
        File(gameDir, "main.py").delete()
        File(gameDir, "renpy").deleteRecursively()
        File(gameDir, "lib").deleteRecursively()
        File(gameDir, "include").deleteRecursively()
        File(gameDir, ".runtime_699.version").delete()
        File(gameDir, ".runtime_7411.version").delete()
        File(gameDir, ".runtime_784.version").delete()
        File(gameDir, ".runtime_837.version").delete()
        File(gameDir, "private.version").delete()
        File(gameDir, ".private.version").delete()
    }

    private fun extractTarGz(context: Context, assetName: String, targetDir: File): Boolean {
        targetDir.mkdirs()
        val buffer = ByteArray(1024 * 512)

        return try {
            context.assets.open(assetName).use { assetIn ->
                BufferedInputStream(assetIn).use { bis ->
                    GZIPInputStream(bis).use { gzis ->
                        TarInputStream(BufferedInputStream(gzis)).use { tis ->
                            var entry: TarEntry? = tis.nextEntry
                            while (entry != null) {
                                val destFile = File(targetDir, entry.name)
                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    FileOutputStream(destFile).use { fos ->
                                        BufferedOutputStream(fos).use { bos ->
                                            var len: Int
                                            while (tis.read(buffer).also { len = it } != -1) {
                                                bos.write(buffer, 0, len)
                                            }
                                        }
                                    }
                                }
                                entry = tis.nextEntry
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $assetName to ${targetDir.absolutePath}", e)
            false
        }
    }

    internal fun cleanForeignBinaries(gameDir: File) {
        val libDir = File(gameDir, "lib")
        if (!libDir.exists() || !libDir.isDirectory) return

        val foreignDirs = listOf(
            "windows-i686", "windows-x86_64",
            "linux-i686", "linux-x86_64", "linux-armv7l", "linux-aarch64",
            "darwin-x86_64", "darwin-arm64"
        )

        for (dirName in foreignDirs) {
            val d = File(libDir, dirName)
            if (d.exists()) {
                d.deleteRecursively()
            }
        }
    }
}
