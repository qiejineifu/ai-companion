package com.aicompanion.core.common

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.net.HttpURLConnection

object ImageGenerator {
    private const val TAG = "ImageGenerator"

    suspend fun generate(context: Context, config: ImageGenSettings, prompt: String): String? {
        if (!config.enabled || config.apiKeyEncrypted.isBlank()) return null

        val apiKey = try {
            val bytes = Base64.decode(config.apiKeyEncrypted, Base64.NO_WRAP)
            CryptoUtil.decrypt(bytes, CryptoUtil.getOrCreateKey())
        } catch (_: Exception) { config.apiKeyEncrypted }

        return try {
            val imageUrl = when (config.provider) {
                "dashscope" -> callDashScopeApi(config.apiUrl, apiKey, config.model, prompt, config.size)
                "modelscope" -> callModelScopeApi(config.apiUrl, apiKey, config.model, prompt, config.size)
                else -> callOpenAIApi(config.apiUrl, apiKey, config.model, prompt, config.size)
            } ?: return null
            downloadImage(context, imageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed: ${e.message}", e)
            null
        }
    }

    // === OpenAI-compatible API (SiliconFlow, etc.) ===
    private fun callOpenAIApi(apiUrl: String, apiKey: String, model: String, prompt: String, size: String): String? {
        val url = if (apiUrl.endsWith("/images/generations")) apiUrl
        else "${apiUrl.trimEnd('/')}/v1/images/generations"

        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") }
        val body = """{"model":"$model","prompt":"${esc(prompt)}","n":1,"size":"$size"}"""

        val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val resp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()
        return org.json.JSONObject(resp).optJSONArray("data")?.optJSONObject(0)?.optString("url")
    }

    // === DashScope API (魔搭社区/阿里云百炼) ===
    private fun callDashScopeApi(apiUrl: String, apiKey: String, model: String, prompt: String, size: String): String? {
        val base = apiUrl.ifBlank { "https://dashscope.aliyuncs.com" }.trimEnd('/')
        val submitUrl = "$base/api/v1/services/aigc/text2image/image-synthesis"

        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") }
        val sizeDash = size.replace("x", "*")
        val body = """{"model":"$model","input":{"prompt":"${esc(prompt)}"},"parameters":{"size":"$sizeDash","n":1}}"""

        // Submit task
        val conn = (java.net.URL(submitUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("X-DashScope-Async", "enable")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val submitResp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()

        val submitJson = org.json.JSONObject(submitResp)
        val taskId = submitJson.optJSONObject("output")?.optString("task_id") ?: return null

        // Poll for result
        val pollUrl = "$base/api/v1/tasks/$taskId"
        repeat(20) { attempt ->
            Thread.sleep(3000)
            val pollConn = (java.net.URL(pollUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 10000; readTimeout = 15000
            }
            val pollResp = pollConn.inputStream.use { it.bufferedReader().readText() }
            pollConn.disconnect()

            val pollJson = org.json.JSONObject(pollResp)
            val status = pollJson.optJSONObject("output")?.optString("task_status")
            Log.d(TAG, "DashScope poll $attempt: status=$status")
            if (status == "SUCCEEDED") {
                return pollJson.optJSONObject("output")?.optJSONArray("results")
                    ?.optJSONObject(0)?.optString("url")
            }
            if (status == "FAILED") return null
        }
        Log.w(TAG, "DashScope task timed out: $taskId")
        return null
    }

    // === ModelScope API (魔搭社区 API Inference) ===
    private fun callModelScopeApi(apiUrl: String, apiKey: String, model: String, prompt: String, size: String): String? {
        val base = apiUrl.ifBlank { "https://api-inference.modelscope.cn" }.trimEnd('/')
        val submitUrl = "$base/v1/images/generations"

        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") }
        val sizeW = size.split("x").firstOrNull() ?: "1024"
        val sizeH = size.split("x").lastOrNull() ?: "1024"
        val body = """{"model":"$model","prompt":"${esc(prompt)}","size":"${sizeW}x${sizeH}","n":1}"""

        // Submit async task
        val conn = (java.net.URL(submitUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("X-ModelScope-Async-Mode", "true")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val submitResp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()

        val taskId = org.json.JSONObject(submitResp).optString("task_id") ?: return null

        // Poll for result
        val pollUrl = "$base/v1/tasks/$taskId"
        repeat(20) { attempt ->
            Thread.sleep(3000)
            val pollConn = (java.net.URL(pollUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("X-ModelScope-Task-Type", "image_generation")
                connectTimeout = 10000; readTimeout = 15000
            }
            val pollResp = pollConn.inputStream.use { it.bufferedReader().readText() }
            pollConn.disconnect()

            val pollJson = org.json.JSONObject(pollResp)
            val status = pollJson.optString("task_status")
            Log.d(TAG, "ModelScope poll $attempt: status=$status")
            if (status == "SUCCEED") {
                return pollJson.optJSONArray("output_images")?.optString(0)
            }
            if (status == "FAILED") {
                Log.e(TAG, "ModelScope task failed: $pollResp")
                return null
            }
        }
        return null
    }

    private fun downloadImage(context: Context, imageUrl: String): String? {
        val dir = File(context.filesDir, "generated_images")
        dir.mkdirs()
        val file = File(dir, "img_${System.currentTimeMillis()}.png")
        val conn = (java.net.URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000; readTimeout = 120000
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        file.writeBytes(bytes)
        Log.d(TAG, "Image saved: ${file.absolutePath} (${bytes.size} bytes)")
        return file.toURI().toString()
    }
}
