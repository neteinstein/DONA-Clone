package com.neteinstein.donaclone.core.network.socket

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * The original app connects to `wss://` with a no-op [X509TrustManager] and hostname
 * verification disabled (see protocol notes §2.1) because the DPU hub typically serves a
 * self-signed certificate on the LAN. We replicate that so a replacement client keeps working
 * against the same hardware, but keep it isolated here and opt-in per connection
 * ([com.neteinstein.donaclone.core.network.socket.DomotalkSocket.connect]'s
 * `trustAllCertificates` parameter) rather than applied globally to every HTTPS call the app
 * makes, since it is only appropriate for a known local device on a trusted network.
 */
internal object TrustAllCerts {

    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val sslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }.socketFactory
    }

    val x509TrustManager: X509TrustManager get() = trustManager

    val hostnameVerifier = HostnameVerifier { _, _ -> true }
}
