package id.batitong.batitongtv.security

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Jembatan Kotlin ke modul C++ native (CMake) untuk penyimpanan rahasia terpecah
 * dan perhitungan SHA-256 sertifikat penandatangan (§7-C2 & §7-C3).
 */
class NativeSecurityBridge {

    companion object {
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("batitongtv_security")
                isNativeLoaded = true
            } catch (e: Throwable) {
                isNativeLoaded = false
            }
        }

        // Endpoint gateway default yang terenkripsi saat produksi
        const val DEFAULT_GATEWAY_URL = "https://gateway.batitong.tv"
    }

    external fun getSecretKeyChunks(): ByteArray
    external fun getEncryptedGatewayPayload(): ByteArray
    external fun computeNativeCertSha256(context: Context): String

    /**
     * Mengambil endpoint gateway secara aman:
     * Didekripsi runtime di memori dari blob ciphertext menggunakan kunci rahasia terpecah.
     */
    fun resolveGatewayEndpoint(): String {
        if (!isNativeLoaded) {
            return DEFAULT_GATEWAY_URL
        }

        return try {
            val keyBytes = getSecretKeyChunks()
            val payload = getEncryptedGatewayPayload()

            if (payload.size <= 28) { // 12-byte IV + min 1-byte cipher + 16-byte GCM tag
                return DEFAULT_GATEWAY_URL
            }

            val iv = payload.copyOfRange(0, 12)
            val cipherWithTag = payload.copyOfRange(12, payload.size)

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherWithTag)
            String(decryptedBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            DEFAULT_GATEWAY_URL
        }
    }

    /**
     * Mengambil hash SHA-256 sertifikat penandatangan APK dari native C++.
     * Jika native library tidak tersedia (misal JVM unit test), fallback ke PackageManager tingkat platform.
     */
    fun getSigningCertificateSha256(context: Context): String {
        if (isNativeLoaded) {
            try {
                val cert = computeNativeCertSha256(context)
                if (cert.isNotBlank()) return cert
            } catch (e: Exception) {
                // Fallback ke Java implementation
            }
        }
        return computeJavaCertSha256(context)
    }

    private fun computeJavaCertSha256(context: Context): String {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(pkgName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkSigningCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(pkgName, android.content.pm.PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certBytes = signatures[0].toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(certBytes)
                digest.joinToString("") { "%02X".format(it) }
            } else {
                "TEST_CERTIFICATE_SHA256_PLACEHOLDER"
            }
        } catch (e: Exception) {
            "TEST_CERTIFICATE_SHA256_PLACEHOLDER"
        }
    }
}
