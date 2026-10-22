package id.batitong.batitongtv.data.model

/**
 * Tipe manifest streaming video.
 */
enum class ManifestType {
    DASH,
    HLS,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ManifestType {
            if (value.isNullOrBlank()) return UNKNOWN
            return when (value.trim().lowercase()) {
                "mpd", "dash", "application/dash+xml" -> DASH
                "m3u8", "hls", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HLS
                else -> UNKNOWN
            }
        }

        fun inferFromUrl(url: String): ManifestType {
            val cleanUrl = url.substringBefore('?').substringBefore('|').lowercase()
            return when {
                cleanUrl.endsWith(".mpd") -> DASH
                cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".m3u") -> HLS
                else -> UNKNOWN
            }
        }
    }
}
