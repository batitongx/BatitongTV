package id.batitong.batitongtv.data.model

import java.io.Serializable

/**
 * Model data Channel resmi BatitongTV sesuai spesifikasi §5.
 */
data class Channel(
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val group: String,
    val tvgId: String?,
    val tvgName: String?,
    val headers: MutableMap<String, String> = mutableMapOf(),
    val manifestType: ManifestType = ManifestType.UNKNOWN,
    val metadata: Map<String, String> = emptyMap(),
    val drm: DrmConfig? = null,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0
) : Serializable {

    val id: String
        get() = "${group}_${name}_${streamUrl.hashCode()}"

    /**
     * Helper untuk mengambil User-Agent atau fallback ke default BatitongTV.
     */
    fun getUserAgent(defaultUserAgent: String): String {
        return headers["User-Agent"] ?: defaultUserAgent
    }

    /**
     * Helper untuk memeriksa apakah saluran ini memiliki proteksi DRM.
     */
    val hasDrm: Boolean
        get() = drm != null
}
