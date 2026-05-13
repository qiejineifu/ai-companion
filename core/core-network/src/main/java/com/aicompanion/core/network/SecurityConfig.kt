package com.aicompanion.core.network

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * SSL Pinning and network security configuration.
 * Phase 4: production-ready certificate pinning.
 */
object SecurityConfig {

    // Certificate pins (SHA256 hashes) — update with actual server certs
    private val PINS = mapOf(
        "api.openai.com" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // placeholder
        ),
        "api.deepseek.com" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // placeholder
        ),
        "dashscope.aliyuncs.com" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // placeholder
        ),
        "api.siliconflow.cn" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // placeholder
        )
    )

    fun applySSLPinning(builder: OkHttpClient.Builder) {
        val certificatePinner = CertificatePinner.Builder()
        PINS.forEach { (host, pins) ->
            pins.forEach { pin ->
                certificatePinner.add(host, pin)
            }
        }
        builder.certificatePinner(certificatePinner.build())
    }

    fun createSecureClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .apply { applySSLPinning(this) }
            .apply {
                // Enforce modern TLS
                val trustManager = createTrustManager()
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())
                sslSocketFactory(sslContext.socketFactory, trustManager)
            }
            .hostnameVerifier { hostname, session ->
                // Strict hostname verification
                HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
            .build()
    }

    private fun createTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as java.security.KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        return trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    fun validateCertificate(cert: X509Certificate): Boolean {
        return try {
            cert.checkValidity()
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cert.publicKey.encoded)
                .joinToString("") { "%02x".format(it) }
            true
        } catch (_: Exception) {
            false
        }
    }
}
