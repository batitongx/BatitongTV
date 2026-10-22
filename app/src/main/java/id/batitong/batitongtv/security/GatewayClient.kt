package id.batitong.batitongtv.security

import android.content.Context
import id.batitong.batitongtv.data.model.PlaylistResult
import id.batitong.batitongtv.data.parser.PlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Klien komunikasi aman ke Cloudflare Worker Gateway (§7-C1 s/d C7).
 */
class GatewayClient(
    private val context: Context,
    private val nativeBridge: NativeSecurityBridge = NativeSecurityBridge(),
    private val cacheManager: EncryptedCacheManager = EncryptedCacheManager(context),
    private val playlistParser: PlaylistParser = PlaylistParser(),
    private val httpClient: OkHttpClient = CertificatePinningConfig.createPinnedOkHttpClient()
) {

    private var currentToken: String? = null
    private var currentSessionKeyHex: String? = null
    private var tokenExpiryEpochSec: Long = 0L

    val isTokenValid: Boolean
        get() {
            val nowSec = System.currentTimeMillis() / 1000
            return !currentToken.isNullOrBlank() && (tokenExpiryEpochSec - nowSec) > 30 // Buffer 30 detik
        }

    /**
     * Mengambil ID instalasi unik acak (dibuat sekali, disimpan secara privat).
     */
    fun getOrCreateInstallationId(): String {
        val file = File(context.filesDir, "inst_identity.bin")
        if (file.exists()) {
            val id = file.readText().trim()
            if (id.isNotEmpty()) return id
        }
        val newId = UUID.randomUUID().toString()
        try {
            file.writeText(newId)
        } catch (e: Exception) {
            // Ignore
        }
        return newId
    }

    /**
     * Memperoleh sesi token baru melalui POST /auth (§7-C1).
     */
    suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        val endpoint = nativeBridge.resolveGatewayEndpoint()
        val authUrl = "$endpoint/auth"
        val installId = getOrCreateInstallationId()
        val certSha256 = nativeBridge.getSigningCertificateSha256(context)
        val risk = RiskSignalCollector.collectSignals()

        val jsonBody = JSONObject().apply {
            put("installId", installId)
            put("certSha256", certSha256)
            put("riskSignals", JSONObject().apply {
                put("isRooted", risk.isRooted)
                put("hasFrida", risk.hasFrida)
                put("hasXposed", risk.hasXposed)
                put("isDebuggerAttached", risk.isDebuggerAttached)
                put("isEmulator", risk.isEmulator)
            })
        }

        val request = Request.Builder()
            .url(authUrl)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val resBody = response.body?.string() ?: return@withContext false
                val resJson = JSONObject(resBody)

                currentToken = resJson.getString("token")
                currentSessionKeyHex = resJson.getString("sessionKey")
                val expiresInSec = resJson.optLong("expiresIn", 600L)
                tokenExpiryEpochSec = (System.currentTimeMillis() / 1000) + expiresInSec
                return@withContext true
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Mengunduh dan mendekripsi playlist dari gateway (§7-C1 & C2).
     * Jika jaringan atau gateway tidak tersedia, beralih ke cache terenkripsi Keystore (§7-C7).
     */
    suspend fun fetchPlaylist(): Result<PlaylistResult> = withContext(Dispatchers.IO) {
        // Cek atau perbarui token bila perlu
        if (!isTokenValid) {
            val authSuccess = authenticate()
            if (!authSuccess) {
                // Jalur degradasi offline: Coba muat dari cache terenkripsi
                val cached = cacheManager.loadEncryptedCache()
                return@withContext if (cached != null) {
                    Result.success(playlistParser.parse(cached))
                } else {
                    Result.failure(Exception("Gagal autentikasi dan tidak ada cache valid."))
                }
            }
        }

        val endpoint = nativeBridge.resolveGatewayEndpoint()
        val playlistUrl = "$endpoint/playlist"

        val request = Request.Builder()
            .url(playlistUrl)
            .header("Authorization", "Bearer $currentToken")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Fallback ke cache terenkripsi bila respons server error
                    val cached = cacheManager.loadEncryptedCache()
                    return@withContext if (cached != null) {
                        Result.success(playlistParser.parse(cached))
                    } else {
                        Result.failure(Exception("Gateway error: ${response.code}"))
                    }
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Respons kosong"))
                val json = JSONObject(bodyStr)
                val ivB64 = json.getString("iv")
                val cipherB64 = json.getString("ciphertext")

                // Dekripsi di memori saja dengan session key (C2)
                val plainM3u = decryptPlaylistPayload(ivB64, cipherB64, currentSessionKeyHex!!)

                // Simpan salinan terenkripsi ke cache dengan TTL 24 jam (C2 & C7)
                cacheManager.saveEncryptedCache(plainM3u)

                // Parse langsung di RAM
                val parsed = playlistParser.parse(plainM3u)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            // Fallback ke cache terenkripsi pada kegagalan koneksi
            val cached = cacheManager.loadEncryptedCache()
            if (cached != null) {
                Result.success(playlistParser.parse(cached))
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Mendekripsi payload AES-GCM menggunakan Session Key yang diterima dari /auth.
     */
    fun decryptPlaylistPayload(ivB64Url: String, ciphertextB64Url: String, keyHex: String): String {
        val iv = base64UrlDecode(ivB64Url)
        val ciphertextWithTag = base64UrlDecode(ciphertextB64Url)
        val keyBytes = hexToBytes(keyHex)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decrypted = cipher.doFinal(ciphertextWithTag)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun base64UrlDecode(str: String): ByteArray {
        val padded = str.replace('-', '+').replace('_', '/')
        val finalStr = when (padded.length % 4) {
            2 -> "$padded=="
            3 -> "$padded="
            else -> padded
        }
        return Base64.getDecoder().decode(finalStr)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
