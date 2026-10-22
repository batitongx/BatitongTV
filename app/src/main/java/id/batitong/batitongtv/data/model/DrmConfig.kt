package id.batitong.batitongtv.data.model

import java.util.UUID

/**
 * Model ternormalisasi konfigurasi DRM (Sealed Class) sesuai spesifikasi §5.
 */
sealed class DrmConfig {

    abstract val schemeUuid: UUID

    /**
     * ClearKey lokal dengan pasangan key hexadecimal (kidHex -> keyHex)
     * dan payload JSON JWK (JSON Web Key) terkompilasi untuk LocalMediaDrmCallback.
     */
    data class ClearKey(
        val clearKeys: Map<String, String>, // Map<kidHex, keyHex>
        val jwkJson: String? = null
    ) : DrmConfig() {
        override val schemeUuid: UUID = DrmType.CLEARKEY.schemeUuid
    }

    /**
     * Server lisensi online (Widevine, PlayReady, atau remote ClearKey)
     * dengan URL lisensi dan header HTTP khusus (misal Authorization, User-Agent).
     */
    data class LicenseServer(
        val licenseType: DrmType,
        val url: String,
        val licenseHeaders: Map<String, String> = emptyMap()
    ) : DrmConfig() {
        override val schemeUuid: UUID = licenseType.schemeUuid
    }
}
