package id.batitong.batitongtv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Model DTO format JSON lama untuk kompatibilitas backward (§5).
 */
data class LegacyPlaylistDto(
    @SerializedName("categories")
    val categories: List<LegacyCategoryDto>? = null,

    @SerializedName("channels")
    val channels: List<LegacyChannelDto>? = null,

    @SerializedName("drm_licenses")
    val drmLicenses: List<LegacyDrmLicenseDto>? = null
)

data class LegacyCategoryDto(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("channels")
    val channels: List<LegacyChannelDto>? = null
)

data class LegacyChannelDto(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("stream_url")
    val streamUrl: String? = null,

    @SerializedName("logo_url")
    val logoUrl: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("tvg_id")
    val tvgId: String? = null,

    @SerializedName("tvg_name")
    val tvgName: String? = null,

    @SerializedName("drm_id")
    val drmId: String? = null,

    @SerializedName("user_agent")
    val userAgent: String? = null,

    @SerializedName("referer")
    val referer: String? = null,

    @SerializedName("headers")
    val headers: Map<String, String>? = null
)

data class LegacyDrmLicenseDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("key")
    val key: String? = null,

    @SerializedName("headers")
    val headers: Map<String, String>? = null
)
