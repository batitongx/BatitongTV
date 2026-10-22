package id.batitong.batitongtv.data.parser

import id.batitong.batitongtv.data.model.Category
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.data.model.DrmConfig
import id.batitong.batitongtv.data.model.ManifestType
import id.batitong.batitongtv.data.model.ParsingWarning
import id.batitong.batitongtv.data.model.PlaylistResult
import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parser M3U tangguh yang menutup celah G1–G10 secara menyeluruh:
 * - G1: #EXTHTTP JSON parsing + canonicalization + resilient warning
 * - G2: #KODIPROP full parsing + prefix kodiprop:
 * - G3: #EXTVLCOPT case-insensitive (http-referer / http-referrer / http-user-agent)
 * - G4: #EXTINF generic regex, NO NAME, UNCATEGORIZED, duplicate numbering
 * - G5: DUA dialek pipe URL & stream_headers, URL decoding, hierarki prioritas
 * - G6: State machine berbasis pending accumulator (anti-bocor antar-channel)
 * - G7: Normalizer license_key (4 varian: multi-key, base64, JWK, URL pipe)
 * - G8: Normalisasi DRM type & scheme UUID
 * - G9: Header arbitrer end-to-end
 */
class M3uParser {

    private val extInfAttrPattern = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    /**
     * Akumulator sementara untuk mengumpulkan tag dan metadata sebuah channel
     * sebelum URL stream ditemukan (G6 Anti-Bocor).
     */
    private class ChannelAccumulator {
        var rawName: String? = null
        var logoUrl: String? = null
        var tvgId: String? = null
        var tvgName: String? = null
        var groupTitle: String? = null
        var extGrp: String? = null

        // Header per sumber dengan hierarki prioritas G5
        val vlcHeaders = mutableMapOf<String, String>()
        val httpHeaders = mutableMapOf<String, String>()
        val streamHeaders = mutableMapOf<String, String>()

        // Metadata umum (kodiprop:*, extinf:*, dll)
        val metadata = mutableMapOf<String, String>()

        // Properti Kodi / DRM
        var kodiLicenseType: String? = null
        var kodiLicenseKey: String? = null
        var kodiManifestType: String? = null

        fun reset() {
            rawName = null
            logoUrl = null
            tvgId = null
            tvgName = null
            groupTitle = null
            extGrp = null
            vlcHeaders.clear()
            httpHeaders.clear()
            streamHeaders.clear()
            metadata.clear()
            kodiLicenseType = null
            kodiLicenseKey = null
            kodiManifestType = null
        }
    }

    fun parse(content: String): PlaylistResult {
        val channels = mutableListOf<Channel>()
        val warnings = mutableListOf<ParsingWarning>()
        val categoryChannelCount = mutableMapOf<String, MutableMap<String, Int>>()

        val reader = BufferedReader(StringReader(content))
        var lineNum = 0
        var currentAcc = ChannelAccumulator()

        var rawLine: String?
        while (reader.readLine().also { rawLine = it } != null) {
            lineNum++
            val line = rawLine?.trim() ?: continue
            if (line.isEmpty()) continue

            // Abaikan header file M3U
            if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                continue
            }

            // G1: #EXTHTTP: { JSON body }
            if (line.startsWith("#EXTHTTP:", ignoreCase = true)) {
                val jsonPart = line.substring(9).trim()
                parseExtHttp(jsonPart, currentAcc, lineNum, warnings)
                continue
            }

            // G2: #KODIPROP: key=value (Bisa muncul sebelum atau sesudah #EXTINF)
            if (line.startsWith("#KODIPROP:", ignoreCase = true)) {
                val propPart = line.substring(10).trim()
                parseKodiProp(propPart, currentAcc, lineNum, warnings)
                continue
            }

            // G3: #EXTVLCOPT: key=value
            if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val optPart = line.substring(11).trim()
                parseExtVlcOpt(optPart, currentAcc)
                continue
            }

            // #EXTGRP: Group Name
            if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = line.substring(8).trim()
                if (grp.isNotEmpty()) {
                    currentAcc.extGrp = grp
                }
                continue
            }

            // G4: #EXTINF:
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                parseExtInf(line, currentAcc)
                continue
            }

            // Komentar M3U lainnya yang tidak dikenal
            if (line.startsWith("#")) {
                continue
            }

            // Baris URL Stream: COMMIT AKUMULATOR MENJADI CHANNEL (G6)
            if (isStreamUrlLine(line)) {
                val channel = commitChannel(line, currentAcc, categoryChannelCount, lineNum, warnings)
                channels.add(channel)
                // Reset akumulator segera setelah commit (Anti-Bocor G6)
                currentAcc.reset()
            }
        }

        // Kelompokkan saluran ke dalam objek Category
        val groupedCategories = groupChannelsIntoCategories(channels)

        return PlaylistResult(
            channels = channels,
            categories = groupedCategories,
            warnings = warnings
        )
    }

    private fun isStreamUrlLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("rtmps://") ||
                lower.startsWith("rtsp://") ||
                lower.contains(".m3u8") ||
                lower.contains(".mpd")
    }

    /**
     * G1: Parsing #EXTHTTP body sebagai JSON, kanonisasi header, catat warning jika rusak.
     */
    private fun parseExtHttp(
        jsonStr: String,
        acc: ChannelAccumulator,
        lineNum: Int,
        warnings: MutableList<ParsingWarning>
    ) {
        if (jsonStr.isBlank()) return
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key)
                val canonKey = HeaderCanonicalizer.canonicalizeName(key)
                acc.httpHeaders[canonKey] = HeaderCanonicalizer.decodeValue(value)
            }
        } catch (e: Exception) {
            warnings.add(
                ParsingWarning(
                    lineNumber = lineNum,
                    tag = "EXTHTTP",
                    message = "JSON #EXTHTTP tidak valid atau rusak: ${e.message}",
                    rawContent = jsonStr
                )
            )
        }
    }

    /**
     * G2: Parsing #KODIPROP kunci-kunci kodi, simpan di metadata kodiprop:*
     */
    private fun parseKodiProp(
        propPart: String,
        acc: ChannelAccumulator,
        lineNum: Int,
        warnings: MutableList<ParsingWarning>
    ) {
        if (!propPart.contains('=')) return
        val key = propPart.substringBefore('=').trim()
        val value = propPart.substringAfter('=').trim()
        val lowerKey = key.lowercase(Locale.ROOT)

        // Simpan semua properti ke metadata dengan prefix kodiprop:
        acc.metadata["kodiprop:$key"] = value

        when (lowerKey) {
            "inputstream.adaptive.license_type" -> {
                acc.kodiLicenseType = value
            }
            "inputstream.adaptive.license_key" -> {
                acc.kodiLicenseKey = value
            }
            "inputstream.adaptive.manifest_type" -> {
                acc.kodiManifestType = value
            }
            "inputstream.adaptive.stream_headers" -> {
                // Parsing format stream_headers (DUA dialek G5)
                val parsedHeaders = HeaderCanonicalizer.parseStreamHeaders(value)
                acc.streamHeaders.putAll(parsedHeaders)
            }
        }
    }

    /**
     * G3: Parsing #EXTVLCOPT kunci http-user-agent, http-referrer, http-referer (1 r), http-origin
     */
    private fun parseExtVlcOpt(optPart: String, acc: ChannelAccumulator) {
        if (!optPart.contains('=')) return
        val key = optPart.substringBefore('=').trim()
        val value = optPart.substringAfter('=').trim()

        val canonKey = HeaderCanonicalizer.canonicalizeName(key)
        val decodedVal = HeaderCanonicalizer.decodeValue(value)
        acc.vlcHeaders[canonKey] = decodedVal
    }

    /**
     * G4: Parsing #EXTINF atribut generik & nama saluran sesudah koma terakhir.
     */
    private fun parseExtInf(line: String, acc: ChannelAccumulator) {
        // Ambil bagian info sebelum koma terakhir dan nama sesudah koma terakhir
        val lastCommaIdx = line.lastIndexOf(',')
        if (lastCommaIdx != -1) {
            val namePart = line.substring(lastCommaIdx + 1).trim()
            if (namePart.isNotEmpty()) {
                acc.rawName = namePart
            }
        }

        val tagContent = if (lastCommaIdx != -1) line.substring(0, lastCommaIdx) else line
        val matcher = extInfAttrPattern.matcher(tagContent)

        while (matcher.find()) {
            val attrName = matcher.group(1)?.trim() ?: continue
            val attrValue = matcher.group(2)?.trim() ?: ""

            when (attrName.lowercase(Locale.ROOT)) {
                "tvg-id" -> acc.tvgId = attrValue
                "tvg-name" -> acc.tvgName = attrValue
                "tvg-logo" -> acc.logoUrl = attrValue
                "group-title" -> acc.groupTitle = attrValue
                else -> {
                    // Atribut asing dipetakan ke metadata dengan prefix extinf:
                    acc.metadata["extinf:$attrName"] = attrValue
                }
            }
        }
    }

    /**
     * Commit akumulator dan baris URL menjadi Channel utuh sesuai G5, G6, G7, G8.
     */
    private fun commitChannel(
        rawUrlLine: String,
        acc: ChannelAccumulator,
        categoryChannelCount: MutableMap<String, MutableMap<String, Int>>,
        lineNum: Int,
        warnings: MutableList<ParsingWarning>
    ): Channel {
        // Pisahkan URL dan sufiks pipe (G5)
        val streamUrl: String
        val pipeHeaders = mutableMapOf<String, String>()

        if (rawUrlLine.contains('|')) {
            val pipeIdx = rawUrlLine.indexOf('|')
            streamUrl = rawUrlLine.substring(0, pipeIdx).trim()
            val pipeSuffix = rawUrlLine.substring(pipeIdx + 1).trim()
            pipeHeaders.putAll(HeaderCanonicalizer.parsePipeSuffix(pipeSuffix))
        } else {
            streamUrl = rawUrlLine.trim()
        }

        // Tentukan grup kategori: group-title atau #EXTGRP atau UNCATEGORIZED
        val group = when {
            !acc.groupTitle.isNullOrBlank() -> acc.groupTitle!!.trim()
            !acc.extGrp.isNullOrBlank() -> acc.extGrp!!.trim()
            else -> "UNCATEGORIZED"
        }

        // Tentukan nama channel: nama sesudah koma atau tvg-name atau NO NAME
        val baseName = when {
            !acc.rawName.isNullOrBlank() -> acc.rawName!!.trim()
            !acc.tvgName.isNullOrBlank() -> acc.tvgName!!.trim()
            else -> "NO NAME"
        }

        // Penomoran duplikat dalam satu kategori (#1, #2, dst.) sesuai G4
        val nameCounts = categoryChannelCount.getOrPut(group) { mutableMapOf() }
        val count = nameCounts.getOrDefault(baseName, 0)
        nameCounts[baseName] = count + 1
        val finalName = if (count > 0) "$baseName #$count" else baseName

        // Penggabungan header sesuai hierarki prioritas G5 (rendah -> tinggi):
        // #EXTVLCOPT -> #EXTHTTP -> stream_headers -> sufiks pipe URL
        val combinedHeaders = mutableMapOf<String, String>()
        combinedHeaders.putAll(acc.vlcHeaders)
        combinedHeaders.putAll(acc.httpHeaders)
        combinedHeaders.putAll(acc.streamHeaders)
        combinedHeaders.putAll(pipeHeaders)

        // Deteksi ManifestType
        val manifestType = when {
            acc.kodiManifestType != null -> ManifestType.fromString(acc.kodiManifestType)
            else -> ManifestType.inferFromUrl(streamUrl)
        }

        // Normalisasi DRM (G7 & G8)
        val drmType = DrmNormalizer.normalizeDrmType(
            acc.kodiLicenseType,
            lineNumber = lineNum,
            warnings = warnings
        )
        val drmConfig = if (acc.kodiLicenseKey != null) {
            DrmNormalizer.normalizeLicense(
                drmType = drmType,
                licenseKey = acc.kodiLicenseKey,
                licenseTypeStr = acc.kodiLicenseType,
                lineNumber = lineNum,
                warnings = warnings
            )
        } else null

        return Channel(
            name = finalName,
            logoUrl = acc.logoUrl,
            streamUrl = streamUrl,
            group = group,
            tvgId = acc.tvgId,
            tvgName = acc.tvgName,
            headers = combinedHeaders,
            manifestType = manifestType,
            metadata = acc.metadata.toMap(),
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
