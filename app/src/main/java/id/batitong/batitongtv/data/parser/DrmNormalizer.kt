package id.batitong.batitongtv.data.parser

import id.batitong.batitongtv.data.model.DrmConfig
import id.batitong.batitongtv.data.model.DrmType
import id.batitong.batitongtv.data.model.ParsingWarning
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/**
 * Utilitas normalisasi konfigurasi DRM dan license_key sesuai spesifikasi G7 dan G8.
 */
object DrmNormalizer {

    /**
     * Menormalisasi tipe DRM dari string ke enum DrmType (G8).
     * Jika tidak dikenali, mencatat warning terstruktur dan mengembalikan null (tidak menjadi UUID_NIL).
     */
    fun normalizeDrmType(
        rawType: String?,
        lineNumber: Int = 0,
        warnings: MutableList<ParsingWarning>? = null
    ): DrmType? {
        if (rawType.isNullOrBlank()) return null
        val type = DrmType.fromString(rawType)
        if (type == null) {
            warnings?.add(
                ParsingWarning(
                    lineNumber = lineNumber,
                    tag = "DRM",
                    message = "Tipe DRM tidak dikenal: '$rawType'. Diabaikan.",
                    rawContent = rawType
                )
            )
        }
        return type
    }

    /**
     * Menormalisasi nilai license_key dari 4 varian G7:
     * (i) Pasangan ClearKey hex "kid:key", termasuk multi-key dipisah koma
     * (ii) Pasangan Base64/Base64URL per sisi -> dikonversi ke hex
     * (iii) JSON inline JWK {"keys":[{"kty":"oct","kid":...,"k":...}]}
     * (iv) URL endpoint (ClearKey maupun server lisensi Widevine/PlayReady) dengan sufiks pipe headers
     */
    fun normalizeLicense(
        drmType: DrmType?,
        licenseKey: String?,
        licenseTypeStr: String? = null,
        lineNumber: Int = 0,
        warnings: MutableList<ParsingWarning>? = null
    ): DrmConfig? {
        if (licenseKey.isNullOrBlank()) return null
        val raw = licenseKey.trim()

        // Varian (iv): URL endpoint (http:// atau https://)
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            val urlPart = raw.substringBefore('|').trim()
            val headerPart = if (raw.contains('|')) raw.substringAfter('|').trim() else ""
            val headers = if (headerPart.isNotEmpty()) {
                HeaderCanonicalizer.parseStreamHeaders(headerPart)
            } else {
                emptyMap()
            }

            val targetType = drmType ?: DrmType.WIDEVINE // Default jika endpoint server lisensi
            return DrmConfig.LicenseServer(
                licenseType = targetType,
                url = urlPart,
                licenseHeaders = headers
            )
        }

        // Varian (iii): JSON inline JWK
        if (raw.startsWith("{") && raw.contains("\"keys\"")) {
            try {
                val json = JSONObject(raw)
                val keysArray = json.optJSONArray("keys")
                val clearKeys = mutableMapOf<String, String>()

                if (keysArray != null) {
                    for (i in 0 until keysArray.length()) {
                        val keyObj = keysArray.getJSONObject(i)
                        val kidBase64 = keyObj.optString("kid")
                        val kBase64 = keyObj.optString("k")
                        if (kidBase64.isNotEmpty() && kBase64.isNotEmpty()) {
                            val kidHex = base64UrlToHex(kidBase64)
                            val keyHex = base64UrlToHex(kBase64)
                            clearKeys[kidHex] = keyHex
                        }
                    }
                }
                return DrmConfig.ClearKey(
                    clearKeys = clearKeys,
                    jwkJson = raw
                )
            } catch (e: Exception) {
                warnings?.add(
                    ParsingWarning(
                        lineNumber = lineNumber,
                        tag = "DRM_JWK",
                        message = "Gagal mem-parsing payload JSON inline JWK: ${e.message}",
                        rawContent = raw
                    )
                )
            }
        }

        // Varian (i) & (ii): Pasangan ClearKey kid:key (termasuk multi-key dipisah koma)
        if (raw.contains(':')) {
            val clearKeys = mutableMapOf<String, String>()
            val pairs = raw.split(',')

            for (pair in pairs) {
                val p = pair.trim()
                if (!p.contains(':')) continue
                val rawKid = p.substringBefore(':').trim()
                val rawKey = p.substringAfter(':').trim()

                val kidHex = normalizeKeySegment(rawKid)
                val keyHex = normalizeKeySegment(rawKey)

                if (kidHex.isNotEmpty() && keyHex.isNotEmpty()) {
                    clearKeys[kidHex.lowercase(Locale.ROOT)] = keyHex.lowercase(Locale.ROOT)
                }
            }

            if (clearKeys.isNotEmpty()) {
                val compiledJwk = buildJwkPayload(clearKeys)
                return DrmConfig.ClearKey(
                    clearKeys = clearKeys,
                    jwkJson = compiledJwk
                )
            }
        }

        // Jika tidak cocok dengan format di atas namun ada URL endpoint non-standar
        warnings?.add(
            ParsingWarning(
                lineNumber = lineNumber,
                tag = "DRM_LICENSE",
                message = "Format license_key tidak dapat dinormalisasi: '$raw'",
                rawContent = raw
            )
        )
        return null
    }

    /**
     * Menormalisasi satu segmen kunci (bisa berupa 32 karakter Hex, atau Base64/Base64URL).
     */
    fun normalizeKeySegment(segment: String): String {
        val clean = segment.trim().replace("-", "").replace("_", "")
        // Jika sudah 32 karakter hexadecimal murni
        if (segment.length == 32 && segment.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return segment.lowercase(Locale.ROOT)
        }

        // Coba decode sebagai Base64 / Base64URL
        return try {
            val base64Clean = segment.trim()
                .replace('-', '+')
                .replace('_', '/')
            val padded = when (base64Clean.length % 4) {
                2 -> "$base64Clean=="
                3 -> "$base64Clean="
                else -> base64Clean
            }
            val decoded = Base64.getDecoder().decode(padded)
            bytesToHex(decoded)
        } catch (e: Exception) {
            // Fallback jika hanya hex dengan panjang tidak biasa
            if (segment.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                segment.lowercase(Locale.ROOT)
            } else {
                ""
            }
        }
    }

    /**
     * Membangun payload JWK standar untuk ClearKey:
     * {"keys":[{"kty":"oct","k":"<base64url>","kid":"<base64url>"}],"type":"temporary"}
     */
    fun buildJwkPayload(clearKeys: Map<String, String>): String {
        val root = JSONObject()
        val keysArray = JSONArray()

        for ((kidHex, keyHex) in clearKeys) {
            val keyObj = JSONObject()
            keyObj.put("kty", "oct")
            keyObj.put("kid", hexToBase64Url(kidHex))
            keyObj.put("k", hexToBase64Url(keyHex))
            keysArray.put(keyObj)
        }

        root.put("keys", keysArray)
        root.put("type", "temporary")
        return root.toString()
    }

    private fun hexToBase64Url(hex: String): String {
        val bytes = hexToBytes(hex)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun base64UrlToHex(base64Url: String): String {
        val padded = base64Url.replace('-', '+').replace('_', '/')
        val finalStr = when (padded.length % 4) {
            2 -> "$padded=="
            3 -> "$padded="
            else -> padded
        }
        val bytes = Base64.getDecoder().decode(finalStr)
        return bytesToHex(bytes)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
