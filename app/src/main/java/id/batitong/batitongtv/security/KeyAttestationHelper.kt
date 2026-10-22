package id.batitong.batitongtv.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64

/**
 * Utilitas Android Key Attestation untuk jalur degradasi STB/TV Box non-GMS (§7-C3).
 * Membangkitkan kunci asymmetric di AndroidKeyStore hardware-backed bila tersedia,
 * dan mengekstrak rantai sertifikat atestasi untuk dikirim ke gateway.
 */
object KeyAttestationHelper {

    private const val KEY_ALIAS = "batitong_attestation_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getAttestationCertChain(challenge: ByteArray = "batitong_nonce".toByteArray()): List<String> {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateAttestationKey(challenge)
            }

            val certChain = keyStore.getCertificateChain(KEY_ALIAS)
            certChain?.map { cert ->
                Base64.getEncoder().encodeToString(cert.encoded)
            } ?: emptyList()
        } catch (e: Exception) {
            // Perangkat Android STB sangat lama tanpa TEE hardware attestation
            emptyList()
        }
    }

    private fun generateAttestationKey(challenge: ByteArray) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)

        keyPairGenerator.initialize(specBuilder.build())
        keyPairGenerator.generateKeyPair()
    }
}
