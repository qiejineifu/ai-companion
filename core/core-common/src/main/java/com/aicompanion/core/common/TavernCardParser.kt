package com.aicompanion.core.common

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Parses PNG-embedded Tavern Character Card (v2/v3 spec).
 */
object TavernCardParser {

    data class ParsedCard(
        val name: String,
        val description: String = "",
        val systemPrompt: String = "",
        val scenario: String = "",
        val firstMessage: String = "",
        val exampleChats: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val creator: String = "",
        val specVersion: String = "",
        val personality: String = "",
        val postHistoryInstructions: String = "",
        val rawJson: String = ""
    )

    private val gson = Gson()

    fun parse(context: Context, uri: Uri): ParsedCard? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            return null
        } ?: return null

        val jsonString = extractTavernJson(bytes) ?: return null
        return parseCardJson(jsonString)
    }

    private fun extractTavernJson(bytes: ByteArray): String? {
        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size < 8 || !bytes.take(8).toByteArray().contentEquals(pngSignature)) {
            return try {
                String(bytes, Charsets.UTF_8).trim().let {
                    if (it.startsWith("{")) it else null
                }
            } catch (e: Exception) { null }
        }

        var pos = 8
        while (pos + 8 <= bytes.size) {
            val length = ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
            val type = String(bytes, pos + 4, 4, Charsets.UTF_8)
            pos += 8

            if (pos + length > bytes.size) break

            if (type == "tEXt") {
                val data = bytes.copyOfRange(pos, pos + length)
                val nullIdx = data.indexOf(0.toByte())
                if (nullIdx > 0) {
                    val keyword = String(data, 0, nullIdx, Charsets.UTF_8)
                    val text = String(data, nullIdx + 1, data.size - nullIdx - 1, Charsets.UTF_8)
                    if (keyword == "chara" || keyword == "ccv3") {
                        return try {
                            val decoded = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
                            String(decoded, Charsets.UTF_8)
                        } catch (e: Exception) {
                            text
                        }
                    }
                }
            }

            pos += length + 4
        }
        return null
    }

    private fun jsonStr(obj: JsonObject, key: String): String =
        obj.get(key)?.asString ?: ""

    private fun jsonList(obj: JsonObject, key: String): List<String> =
        obj.getAsJsonArray(key)?.mapNotNull { it?.asString } ?: emptyList()

    private fun parseCardJson(jsonString: String): ParsedCard? {
        return try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)

            val data = if (root.has("data")) {
                root.getAsJsonObject("data") ?: root
            } else {
                root
            }

            val spec = jsonStr(root, "spec")
            val specVersion = jsonStr(root, "spec_version")

            val name = jsonStr(data, "name")
            if (name.isBlank()) return null

            val description = jsonStr(data, "description")
            val personality = jsonStr(data, "personality")
            val scenario = jsonStr(data, "scenario")
            val firstMessage = jsonStr(data, "first_mes")
            val mesExample = jsonStr(data, "mes_example")
            val systemPrompt = jsonStr(data, "system_prompt")
            val postHistory = jsonStr(data, "post_history_instructions")
            val creator = jsonStr(data, "creator")
            val tags = jsonList(data, "tags")

            val systemParts = mutableListOf<String>()
            if (personality.isNotBlank()) systemParts.add(personality)
            if (systemPrompt.isNotBlank()) systemParts.add(systemPrompt)
            val fullSystemPrompt = systemParts.joinToString("\n\n")

            val exampleChats = if (mesExample.isNotBlank()) {
                mesExample.split("\n").filter { it.isNotBlank() }
            } else emptyList()

            ParsedCard(
                name = name,
                description = description,
                systemPrompt = fullSystemPrompt,
                scenario = scenario,
                firstMessage = firstMessage,
                exampleChats = exampleChats,
                tags = tags,
                creator = creator,
                specVersion = if (specVersion.isNotBlank()) "$spec v$specVersion" else spec,
                personality = personality,
                postHistoryInstructions = postHistory,
                rawJson = jsonString
            )
        } catch (e: Exception) {
            null
        }
    }
}
