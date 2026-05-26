package com.aicompanion.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SSEClient(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile var isCancelled = false
        private set

    fun streamChat(
        url: String,
        apiKey: String,
        model: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        isCancelled = false

        val messagesJson = JsonArray(messages.map { msg ->
            buildJsonObject {
                msg.forEach { (key, value) -> put(key, value) }
            }
        })

        val requestBody = buildJsonObject {
            put("model", model)
            put("messages", messagesJson)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", true)
        }

        try {
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(requestBody)
            }

            val statusCode = response.status.value
            if (statusCode !in 200..299) {
                val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
                throw ApiException(statusCode, "API 返回 HTTP $statusCode: ${errorBody.take(300)}")
            }

            // Read response — collect raw lines for diagnostics
            val channel = response.bodyAsChannel()
            val buffer = StringBuilder()
            var chunkCount = 0
            val rawLines = StringBuilder()

            while (!isCancelled && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                rawLines.appendLine(line)

                val dataPrefix = if (line.startsWith("data:")) "data:" else null
                if (dataPrefix != null) {
                    val data = line.removePrefix(dataPrefix).trim()
                    if (data == "[DONE]") break

                    try {
                        val content = json.parseToJsonElement(data)
                            .jsonObject
                            .get("choices")
                            ?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("delta")
                            ?.jsonObject
                            ?.get("content")
                            ?.jsonPrimitive
                            ?.content

                        if (!content.isNullOrEmpty() && content != "null") {
                            chunkCount++
                            buffer.append(content)
                            emit(buffer.toString())
                        }
                    } catch (_: Exception) {
                        // Malformed chunk — include in error context
                    }
                }
            }

            // If no SSE chunks were parsed, the API response format doesn't match
            if (chunkCount == 0) {
                val sample = rawLines.toString().take(500)
                throw ApiException(statusCode, "API 响应未被解析。\nHTTP $statusCode\n首行预览: ${sample.take(300)}")
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            if (!isCancelled) throw e
        }
    }

    fun cancel() {
        isCancelled = true
    }
}

class ApiException(val statusCode: Int, message: String) : Exception(message)
