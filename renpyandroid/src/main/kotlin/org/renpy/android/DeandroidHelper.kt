package org.renpy.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CustomEnvVar(
    var name: String,
    var type: String,
    var value: String
)

object DeandroidHelper {

    const val TYPE_BOOLEAN = "boolean"
    const val TYPE_NUMBER = "number"
    const val TYPE_CUSTOM = "custom"

    private val RENPY_ANDROID_REGEX = Regex("""renpy\.android\s*=\s*(True|False)""", RegexOption.IGNORE_CASE)
    private val VAR_NAME_REGEX = Regex("""^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$""")
    private val NUMBER_REGEX = Regex("""^-?\d+(\.\d+)?$""")

    fun isValidVarName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.equals("renpy.android", ignoreCase = true)) {
            return false
        }
        return VAR_NAME_REGEX.matches(trimmed)
    }

    fun isValidNumber(value: String): Boolean {
        return NUMBER_REGEX.matches(value.trim())
    }

    fun getPatchFile(gameFolder: File): File? {
        if (gameFolder.name == ExperimentsActivity.EXCLUDED_MAS_DIR) {
            return null
        }
        val gameDir = if (gameFolder.name == "game" && gameFolder.isDirectory) {
            gameFolder
        } else {
            File(gameFolder, "game")
        }
        if (!gameDir.exists() || !gameDir.isDirectory) {
            return null
        }
        return File(File(gameDir, "a_masl_patches"), "deandroid.rpy")
    }

    fun isAndroidMode(gameFolder: File): Boolean {
        val patchFile = getPatchFile(gameFolder) ?: return false
        if (!patchFile.exists()) {
            return false
        }
        return try {
            val content = patchFile.readText()
            val match = RENPY_ANDROID_REGEX.find(content)
            match?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getEnvVarsJsonFile(gameFolder: File): File {
        val mineDir = File(gameFolder, ".mine")
        return File(mineDir, "env_vars.json")
    }

    fun getCustomEnvVars(gameFolder: File): List<CustomEnvVar> {
        val file = getEnvVarsJsonFile(gameFolder)
        if (!file.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<CustomEnvVar>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                val type = obj.optString("type", TYPE_CUSTOM).trim()
                val value = obj.optString("value", "").trim()
                if (name.isNotEmpty()) {
                    list.add(CustomEnvVar(name, type, value))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEnvVars(gameFolder: File, isAndroid: Boolean, customVars: List<CustomEnvVar>): Boolean {
        val patchFile = getPatchFile(gameFolder) ?: return false
        return try {
            val mineDir = File(gameFolder, ".mine")
            if (!mineDir.exists()) {
                mineDir.mkdirs()
            }
            val jsonArray = JSONArray()
            for (v in customVars) {
                val obj = JSONObject().apply {
                    put("name", v.name.trim())
                    put("type", v.type.trim())
                    put("value", v.value.trim())
                }
                jsonArray.put(obj)
            }
            File(mineDir, "env_vars.json").writeText(jsonArray.toString(2))

            patchFile.parentFile?.mkdirs()
            val sb = StringBuilder()
            sb.append("init -999 python:\n")
            sb.append("    renpy.android = ${if (isAndroid) "True" else "False"}\n")
            for (v in customVars) {
                val name = v.name.trim()
                val value = v.value.trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    sb.append("    $name = $value\n")
                }
            }
            patchFile.writeText(sb.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setAndroidMode(gameFolder: File, isAndroid: Boolean): Boolean {
        val customVars = getCustomEnvVars(gameFolder)
        return saveEnvVars(gameFolder, isAndroid, customVars)
    }

    fun getFormattedStatus(isAndroid: Boolean, customVarsCount: Int = 0): String {
        val base = "renpy.android = ${if (isAndroid) "True" else "False"}"
        return if (customVarsCount > 0) {
            "$base (+$customVarsCount)"
        } else {
            base
        }
    }
}
