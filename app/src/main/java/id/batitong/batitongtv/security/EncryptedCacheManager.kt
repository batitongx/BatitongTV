package id.batitong.batitongtv.security

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pengelola cache playlist terenkripsi AES-GCM berbasis Android Keystore (§7-C2 & §7-C7).
 * - Plaintext tidak pernah menyentuh disk atau SharedPreferences.
 * - Berkas di disk hanya berisi blob ciphertext AES-GCM.
 * - Memiliki TTL ketat maksimum 24 jam untuk dukungan mode offline.
 */
class EncryptedCacheManager(
    private val context: Context,
    private val overrideKey: SecretKey? = null // Digunakan untuk JVM Unit Testing
) {

    companion object {
        private const val KEY_ALIAS = "batitong_cache_aes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CACHE_FILE_NAME = "encrypted_playlist.bin"
        private const val META_FILE_NAME = "cache_timestamp.bin"
        const val MAX_TTL_MILLIS = 24L * 60 * 60 * 1000 // 24 Jam
    }

    private fun getOrCreateKey(): SecretKey {
        if (overrideKey != null) return overrideKey

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: generateFallbackKey()
            } else {
                generateKeystoreKey()
            }
        } catch (e: Throwable) {
            // Fallback untuk perangkat tanpa Keystore Provider standar atau JVM
            generateFallbackKey()
        }
    }

    private fun generateKeystoreKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            generateFallbackKey()
        }
    }

    private fun generateFallbackKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    /**
     * Menyimpan dokumen playlist secara terenkripsi AES-GCM ke disk.
     */
    fun saveEncryptedCache(plainText: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Gabungkan IV (12 byte) + ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
        cacheFile.writeBytes(combined)

        // Simpan timestamp
        val metaFile = File(context.cacheDir, META_FILE_NAME)
        metaFile.writeText(System.currentTimeMillis().toString())
    }

    /**
     * Memuat dokumen playlist dari cache terenkripsi ke memori.
     * Mengembalikan null jika cache tidak ditemukan atau telah kedaluwarsa (> 24 jam).
     */
    fun loadEncryptedCache(): String? {
        val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
        val metaFile = File(context.cacheDir, META_FILE_NAME)

        if (!cacheFile.exists() || !metaFile.exists()) {
            return null
        }

        // Periksa TTL 24 jam
        val savedTimestamp = metaFile.readText().toLongOrNull() ?: 0L
        val age = System.currentTimeMillis() - savedTimestamp
        if (age < 0 || age > MAX_TTL_MILLIS) {
            clearCache()
            return null
        }

        return try {
            val combined = cacheFile.readBytes()
            if (combined.size <= 12) return null

            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            clearCache()
            null
        }
    }

    /**
     * Memeriksa apakah cache terenkripsi yang masih valid tersedia.
     */
    fun hasValidCache(): Boolean {
        val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
        val metaFile = File(context.cacheDir, META_FILE_NAME)
        if (!cacheFile.exists() || !metaFile.exists()) return false

        val savedTimestamp = metaFile.readText().toLongOrNull() ?: 0L
        val age = System.currentTimeMillis() - savedTimestamp
        return age in 0..MAX_TTL_MILLIS
    }

    /**
     * Menghapus cache terenkripsi dari disk.
     */
    fun clearCache() {
        try {
            File(context.cacheDir, CACHE_FILE_NAME).delete()
            File(context.cacheDir, META_FILE_NAME).delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
