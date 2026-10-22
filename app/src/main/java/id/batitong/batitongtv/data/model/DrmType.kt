package id.batitong.batitongtv.data.model

import java.util.UUID

/**
 * Tipe skema perlindungan konten DRM yang dinormalisasi.
 * Dipetakan langsung ke UUID standar industri AndroidX Media3 (C.CLEARKEY_UUID, C.WIDEVINE_UUID, C.PLAYREADY_UUID).
 */
enum class DrmType(val schemeUuid: UUID) {
    CLEARKEY(UUID.fromString("e2719d58-a985-b3c9-781a-b030e4d41e12")),
    WIDEVINE(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")),
    PLAYREADY(UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95"));

    companion object {
        fun fromString(value: String?): DrmType? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().lowercase()) {
                "org.w3.clearkey", "clearkey", "clear_key" -> CLEARKEY
                "com.widevine.alpha", "widevine", "wv" -> WIDEVINE
                "com.microsoft.playready", "playready", "pr" -> PLAYREADY
                else -> null
            }
        }
    }
}
