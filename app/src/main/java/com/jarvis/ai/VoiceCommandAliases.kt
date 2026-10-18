package com.jarvis.ai

import java.util.Locale
import org.json.JSONObject

object VoiceCommandAliases {
    fun normalize(phrase: String): String = phrase.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun expand(utterance: String, aliases: Map<String, String>): String {
        val key = normalize(utterance)
        return aliases.entries.firstOrNull { normalize(it.key) == key }?.value?.trim().orEmpty().ifBlank { utterance }
    }

    fun toJson(aliases: Map<String, String>): JSONObject = JSONObject().apply {
        aliases.forEach { (alias, command) ->
            val key = normalize(alias)
            val value = command.trim().replace(Regex("\\s+"), " ")
            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
        }
    }

    fun fromJson(json: JSONObject): Map<String, String> = buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val rawAlias = keys.next()
            val alias = normalize(rawAlias)
            val command = json.optString(rawAlias, "").trim().replace(Regex("\\s+"), " ")
            if (alias.isNotBlank() && command.isNotBlank()) put(alias, command)
        }
    }
}
