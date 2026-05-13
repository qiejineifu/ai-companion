package com.aicompanion.core.network

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object HttpClientFactory {

    private val okHttpClient: OkHttpClient by lazy {
        val trustManager = run {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as java.security.KeyStore?)
            tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

        val sslContext = try {
            SSLContext.getInstance("TLSv1.3").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
        } catch (_: Exception) {
            SSLContext.getInstance("TLSv1.2").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
        }

        val protocols = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
        } else {
            listOf(Protocol.HTTP_1_1)
        }

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .protocols(protocols)
            .hostnameVerifier { hostname, session ->
                javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun create(): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Note: Ktor SSE plugin NOT installed — we parse SSE manually to avoid plugin interference
    }
}
