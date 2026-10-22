package id.batitong.batitongtv.data.parser

import com.google.gson.Gson
import id.batitong.batitongtv.data.model.Category
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.data.model.DrmConfig
import id.batitong.batitongtv.data.model.LegacyDrmLicenseDto
import id.batitong.batitongtv.data.model.LegacyPlaylistDto
import id.batitong.batitongtv.data.model.ManifestType
import id.batitong.batitongtv.data.model.ParsingWarning
import id.batitong.batitongtv.data.model.PlaylistResult

/**
 * Parser format JSON lama untuk menjamin kompatibilitas backward (§5).
 * Mendukung format categories[] + drm_licenses[] + drm_id + user_agent/referer.
 */
class LegacyJsonParser(private val gson: Gson = Gson()) {

    fun parse(jsonContent: String): PlaylistResult {
        val warnings = mutableListOf<ParsingWarning>()
        val channels = mutableListOf<Channel>()

        val dto = try {
            gson.fromJson(jsonContent, LegacyPlaylistDto::class.java)
        } catch (e: Exception) {
            warnings.add(
                ParsingWarning(
                    lineNumber = 1,
                    tag = "JSON_PARSE",
                    message = "Gagal mem-parsing dokumen JSON: ${e.message}"
                )
            )
            return PlaylistResult(emptyList(), emptyList(), warnings)
        }

        // Peta DRM License ID -> DTO Lisensi
        val licenseMap = mutableMapOf<String, LegacyDrmLicenseDto>()
        dto.drmLicenses?.forEach { lic ->
            if (!lic.id.isNullOrBlank()) {
                licenseMap[lic.id] = lic
            }
        }

        val categoryChannelCount = mutableMapOf<String, MutableMap<String, Int>>()

        // Jalur 1: Membaca dari kategori bersarang (categories[].channels[])
        dto.categories?.forEach { catDto ->
            val groupName = if (!catDto.name.isNullOrBlank()) catDto.name.trim() else "UNCATEGORIZED"
            catDto.channels?.forEach { chDto ->
                val channel = convertDtoToChannel(chDto, groupName, licenseMap, categoryChannelCount, warnings)
                channels.add(channel)
            }
        }

        // Jalur 2: Membaca dari flat channels[] jika categories[] tidak disediakan
        if (channels.isEmpty() && dto.channels != null) {
            dto.channels.forEach { chDto ->
                val groupName = if (!chDto.category.isNullOrBlank()) chDto.category.trim() else "UNCATEGORIZED"
                val channel = convertDtoToChannel(chDto, groupName, licenseMap, categoryChannelCount, warnings)
                channels.add(channel)
            }
        }

        val groupedCategories = groupChannelsIntoCategories(channels)

        return PlaylistResult(
            channels = channels,
            categories = groupedCategories,
            warnings = warnings
        )
    }

    private fun convertDtoToChannel(
        dto: id.batitong.batitongtv.data.model.LegacyChannelDto,
        group: String,
        licenseMap: Map<String, LegacyDrmLicenseDto>,
        categoryChannelCount: MutableMap<String, MutableMap<String, Int>>,
        warnings: MutableList<ParsingWarning>
    ): Channel {
        val baseName = if (!dto.name.isNullOrBlank()) dto.name.trim() else "NO NAME"
        val nameCounts = categoryChannelCount.getOrPut(group) { mutableMapOf() }
        val count = nameCounts.getOrDefault(baseName, 0)
        nameCounts[baseName] = count + 1
        val finalName = if (count > 0) "$baseName #$count" else baseName

        val streamUrl = dto.streamUrl?.trim() ?: ""

        val headers = mutableMapOf<String, String>()
        if (!dto.userAgent.isNullOrBlank()) {
            headers["User-Agent"] = dto.userAgent.trim()
        }
        if (!dto.referer.isNullOrBlank()) {
            headers["Referer"] = dto.referer.trim()
        }
        dto.headers?.forEach { (k, v) ->
            headers[HeaderCanonicalizer.canonicalizeName(k)] = v
        }

        // Cari DRM lisensi yang dipetakan oleh drm_id
        var drmConfig: DrmConfig? = null
        if (!dto.drmId.isNullOrBlank()) {
            val licDto = licenseMap[dto.drmId]
            if (licDto != null) {
                val drmType = DrmNormalizer.normalizeDrmType(licDto.type, 0, warnings)
                drmConfig = if (!licDto.key.isNullOrBlank()) {
                    DrmNormalizer.normalizeLicense(drmType, licDto.key, licDto.type, 0, warnings)
                } else if (!licDto.url.isNullOrBlank()) {
                    val licHeaders = mutableMapOf<String, String>()
                    licDto.headers?.forEach { (k, v) ->
                        licHeaders[HeaderCanonicalizer.canonicalizeName(k)] = v
                    }
                    val targetType = drmType ?: id.batitong.batitongtv.data.model.DrmType.WIDEVINE
                    DrmConfig.LicenseServer(
                        licenseType = targetType,
                        url = licDto.url.trim(),
                        licenseHeaders = licHeaders
                    )
                } else null
            }
        }

        val manifestType = ManifestType.inferFromUrl(streamUrl)

        return Channel(
            name = finalName,
            logoUrl = dto.logoUrl,
            streamUrl = streamUrl,
            group = group,
            tvgId = dto.tvgId,
            tvgName = dto.tvgName,
            headers = headers,
            manifestType = manifestType,
            metadata = emptyMap(),
            drm = drmConfig
        )
    }

    private fun groupChannelsIntoCategories(channels: List<Channel>): List<Category> {
        val categoryMap = linkedMapOf<String, MutableList<Channel>>()
        for (ch in channels) {
            categoryMap.getOrPut(ch.group) { mutableListOf() }.add(ch)
        }
        return categoryMap.map { (name, chList) ->
            Category(name = name, channels = chList)
        }
    }
}
