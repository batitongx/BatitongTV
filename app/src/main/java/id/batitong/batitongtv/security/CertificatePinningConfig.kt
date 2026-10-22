package id.batitong.batitongtv.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Konfigurasi SPKI Certificate Pinning untuk endpoint first-party (§7-C4).
 * - Setiap host memiliki minimal 2 pin (Aktif + Cadangan).
 * - Tidak mem-pin host pihak ketiga / CDN eksternal untuk mencegah playback error saat rotasi cert CDN.
 */
object CertificatePinningConfig {

    // Domain first-party milik pemilik (Gateway & Server Stream Resmi)
    private val FIRST_PARTY_HOSTS = setOf(
        "gateway.batitong.tv",
        "edge.batitong.tv",
        "stream.batitong.tv",
        "license.batitong.tv"
    )

    // SPKI Pins (SHA-256): Pin Aktif + Pin Cadangan
    private val CERTIFICATE_PINS = mapOf(
        "gateway.batitong.tv" to listOf(
            "sha256/k2oTQLGen4KpWh9Flu2hQGSbCYJ58856vnGLAKTAkts=", // Pin Aktif
            "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18="  // Pin Cadangan
        ),
        "*.batitong.tv" to listOf(
            "sha256/k2oTQLGen4KpWh9Flu2hQGSbCYJ58856vnGLAKTAkts=",
            "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18="
        )
    )

    /**
     * Membangun OkHttp CertificatePinner untuk host first-party.
     */
    fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        for ((pattern, pins) in CERTIFICATE_PINS) {
            for (pin in pins) {
                builder.add(pattern, pin)
            }
        }
        return builder.build()
    }

    /**
     * Memeriksa apakah suatu host merupakan bagian dari infrastruktur first-party.
     */
    fun isFirstPartyHost(urlOrHost: String): Boolean {
        val host = try {
            if (urlOrHost.contains("://")) {
                URI(urlOrHost).host ?: ""
            } else {
                urlOrHost.substringBefore(':')
            }
        } catch (e: Exception) {
            urlOrHost
        }

        val cleanHost = host.lowercase()
        return FIRST_PARTY_HOSTS.contains(cleanHost) || cleanHost.endsWith(".batitong.tv")
    }

    /**
     * Membangun OkHttpClient aman untuk request playlist dan DataSource first-party Media3.
     */
    fun createPinnedOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .certificatePinner(buildCertificatePinner())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * OkHttpClient standar untuk stream pihak ketiga (tanpa pin kaku pihak ketiga).
     */
    fun createStandardOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
