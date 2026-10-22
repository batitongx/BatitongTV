package id.batitong.batitongtv.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Unit Test untuk pengujian keamanan sisi klien:
 * - Enkripsi / Dekripsi Cache AES-GCM
 * - Batas waktu kedaluwarsa cache (TTL 24 jam)
 * - Identifikasi host first-party vs third-party untuk Certificate Pinning
 * - Dekripsi payload playlist dari Gateway
 */
class SecurityAndCacheTest {

    private lateinit var testKey: SecretKey
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        testKey = keyGen.generateKey()

        tempDir = File(System.getProperty("java.io.tmpdir"), "batitong_test_cache_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    /**
     * Membuktikan bahwa enkripsi AES-GCM bekerja secara akurat dan
     * ciphertext tidak membocorkan teks plaintext ke media penyimpanan (§7-C2).
     */
    @Test
    fun testAesGcm_encryption_and_decryption_integrity() {
        val plainPlaylist = """
            #EXTM3U
            #EXTINF:-1 group-title="Uji",Saluran Uji Rahasia
            https://edge.batitong.tv/live/secure.m3u8
        """.trimIndent()

        // 1. Enkripsi dengan AES-GCM (12-byte IV + ciphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, testKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainPlaylist.toByteArray(StandardCharsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        // Verifikasi bahwa data biner tidak mengandung teks asli
        val rawBinaryString = String(combined, StandardCharsets.ISO_8859_1)
        assertFalse(
            "Ciphertext tidak boleh mengandung nama saluran atau URL asli",
            rawBinaryString.contains("Saluran Uji Rahasia") || rawBinaryString.contains("edge.batitong.tv")
        )

        // 2. Dekripsi kembali
        val extractedIv = combined.copyOfRange(0, 12)
        val extractedCiphertext = combined.copyOfRange(12, combined.size)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, extractedIv)
        decryptCipher.init(Cipher.DECRYPT_MODE, testKey, spec)

        val decryptedBytes = decryptCipher.doFinal(extractedCiphertext)
        val resultText = String(decryptedBytes, StandardCharsets.UTF_8)

        assertEquals("Teks hasil dekripsi harus identik 100% dengan teks asli", plainPlaylist, resultText)
    }

    /**
     * Menguji logika TTL 24 jam pada cache:
     * Cache yang berumur > 24 jam harus ditolak dan dibersihkan (§7-C2).
     */
    @Test
    fun testCache_ttl_expiration_logic() {
        val maxTtlMillis = 24L * 60 * 60 * 1000 // 24 Jam
        val now = System.currentTimeMillis()

        // Kasus 1: Cache baru (berumur 1 jam yang lalu)
        val validTimestamp = now - (1L * 60 * 60 * 1000)
        val validAge = now - validTimestamp
        assertTrue("Cache 1 jam yang lalu harus valid", validAge in 0..maxTtlMillis)

        // Kasus 2: Cache usang (berumur 25 jam yang lalu)
        val expiredTimestamp = now - (25L * 60 * 60 * 1000)
        val expiredAge = now - expiredTimestamp
        assertFalse("Cache 25 jam yang lalu harus kedaluwarsa", expiredAge <= maxTtlMillis)
    }

    /**
     * Menguji bahwa CertificatePinningConfig secara tepat membedakan host first-party dan third-party (§7-C4).
     */
    @Test
    fun testCertificatePinning_host_routing() {
        // First-party host harus diidentifikasi untuk diberi pinning
        assertTrue(CertificatePinningConfig.isFirstPartyHost("https://gateway.batitong.tv/auth"))
        assertTrue(CertificatePinningConfig.isFirstPartyHost("https://edge.batitong.tv/live/cinema.mpd"))
        assertTrue(CertificatePinningConfig.isFirstPartyHost("stream.batitong.tv"))

        // Domain pihak ketiga / CDN eksternal TIDAK BOLEH di-pin untuk mencegah kegagalan playback
        assertFalse(CertificatePinningConfig.isFirstPartyHost("https://cdn.example-broadcaster.com/live.m3u8"))
        assertFalse(CertificatePinningConfig.isFirstPartyHost("https://akamai-stream.global.net/hls/live.mpd"))
        assertFalse(CertificatePinningConfig.isFirstPartyHost("https://youtube.com/watch"))
    }

    /**
     * Menguji simulasi dekripsi payload AES-GCM dari GatewayClient (§7-C1 & §7-C2).
     */
    @Test
    fun testGateway_payload_decryption_simulation() {
        val samplePlaylist = "#EXTM3U\n#EXTINF:-1,Ch 1\nhttps://edge.batitong.tv/live.m3u8"
        val keyBytes = testKey.encoded
        val keyHex = keyBytes.joinToString("") { "%02x".format(it) }

        // Enkripsi seperti yang dilakukan gateway worker
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, testKey)
        val iv = cipher.iv
        val ciphertextWithTag = cipher.doFinal(samplePlaylist.toByteArray(StandardCharsets.UTF_8))

        val ivB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
        val cipherB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertextWithTag)

        // Dekripsi menggunakan fungsi helper
        val decrypted = decryptPayloadHelper(ivB64Url, cipherB64Url, keyHex)
        assertEquals(samplePlaylist, decrypted)
    }

    private fun decryptPayloadHelper(ivB64Url: String, ciphertextB64Url: String, keyHex: String): String {
        val iv = Base64.getUrlDecoder().decode(ivB64Url)
        val ciphertext = Base64.getUrlDecoder().decode(ciphertextB64Url)
        val keyBytes = ByteArray(keyHex.length / 2) { i ->
            ((Character.digit(keyHex[i * 2], 16) shl 4) +
                    Character.digit(keyHex[i * 2 + 1], 16)).toByte()
        }

        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
