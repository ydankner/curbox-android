package neth.iecal.curbox.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import neth.iecal.curbox.data.models.Settings

/**
 * Reads and writes the settings backup file. The file wraps the same Gson JSON that DataStore
 * stores, so every field keeps its stored name.
 */
object SettingsBackupCodec {
    private const val FORMAT = "curbox-settings"
    private const val VERSION = 1

    fun encode(settings: Settings, gson: Gson = Gson()): String {
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("settings", gson.toJsonTree(settings))
        }
        return gson.toJson(root)
    }

    /**
     * Gson skips Kotlin default values, so fields missing from the file are filled from
     * [Settings] defaults before decoding. Otherwise they would be null at runtime.
     */
    fun decode(json: String, gson: Gson = Gson()): Settings {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("format")?.asString == FORMAT) { "Not a Curbox settings file" }
        val imported = root.getAsJsonObject("settings")
            ?: throw IllegalArgumentException("Settings are missing")

        val merged = gson.toJsonTree(Settings()).asJsonObject
        imported.entrySet().forEach { (key, value) ->
            if (merged.has(key) && !value.isJsonNull) merged.add(key, value)
        }
        return gson.fromJson(merged, Settings::class.java)
    }
}
