package id.batitong.batitongtv.data.parser

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Utilitas kanonisasi header HTTP sesuai spesifikasi G1, G3, dan G5.
 * Mengubah nama header ke Title-Case per token dan memetakan alias (referer, http-user-agent, dll).
 */
object HeaderCanonicalizer {

    private val WELL_KNOWN_HEADERS = mapOf(
        "user-agent" to "User-Agent",
        "http-user-agent" to "User-Agent",
        "referer" to "Referer",
        "referrer" to "Referer",
        "http-referer" to "Referer",
        "http-referrer" to "Referer",
        "origin" to "Origin",
        "http-origin" to "Origin",
        "authorization" to "Authorization",
        "cookie" to "Cookie",
        "host" to "Host",
        "accept" to "Accept",
        "accept-language" to "Accept-Language",
        "accept-encoding" to "Accept-Encoding",
        "connection" to "Connection",
        "content-type" to "Content-Type",
        "sec-fetch-dest" to "Sec-Fetch-Dest",
        "sec-fetch-mode" to "Sec-Fetch-Mode",
        "sec-fetch-site" to "Sec-Fetch-Site"
    )

    /**
     * Mengkanonisasi nama header menjadi Title-Case per token yang dipisahkan tanda hubung.
     * Contoh: "x-forwarded-for" -> "X-Forwarded-For", "http-referer" -> "Referer"
     */
    fun canonicalizeName(rawName: String): String {
        val trimmed = rawName.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        WELL_KNOWN_HEADERS[lower]?.let { return it }

        // Tangani prefix http-* dari #EXTVLCOPT
        val stripped = if (lower.startsWith("http-")) {
            lower.substring(5)
        } else {
            lower
        }

        // Title-Case per token (contoh: x-auth-token -> X-Auth-Token)
        return stripped.split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    /**
     * Mendekode nilai URL encoded bila terdapat tanda persen (%) sesuai G5.
     */
    fun decodeValue(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (!trimmed.contains('%')) return trimmed
        return try {
            URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            trimmed
        }
    }

    /**
     * Mem-parsing string stream_headers dengan dukungan DUA dialek G5:
     * (1) K=V&K2=V2
     * (2) K=V|K2=V2
     */
    fun parseStreamHeaders(headersStr: String): Map<String, String> {
        if (headersStr.isBlank()) return emptyMap()

        val result = mutableMapOf<String, String>()
        // Tentukan delimiter: periksa apakah mengandung '&' atau '|'
        val tokens = if (headersStr.contains('&')) {
            headersStr.split('&')
        } else {
            headersStr.split('|')
        }

        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty() || !trimmed.contains('=')) continue
            val key = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=').trim()
            if (key.isNotEmpty()) {
                val canonKey = canonicalizeName(key)
                val decodedVal = decodeValue(value)
                result[canonKey] = decodedVal
            }
        }
        return result
    }

    /**
     * Mem-parsing sufiks pipe pada URL stream dengan DUA dialek G5:
     * Dialek 1: url|key=val|key2=val2
     * Dialek 2 (Kodi): url|Key=Val&Key2=Val2
     */
    fun parsePipeSuffix(pipeSuffix: String): Map<String, String> {
        if (pipeSuffix.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()

        if (pipeSuffix.contains('&')) {
            // Dialek Kodi: key1=val1&key2=val2
            val pairs = pipeSuffix.split('&')
            for (p in pairs) {
                if (p.contains('=')) {
                    val k = canonicalizeName(p.substringBefore('=').trim())
                    val v = decodeValue(p.substringAfter('=').trim())
                    result[k] = v
                }
            }
        } else {
            // Dialek berpemisah pipe: key1=val1
            if (pipeSuffix.contains('=')) {
                val k = canonicalizeName(pipeSuffix.substringBefore('=').trim())
                val v = decodeValue(pipeSuffix.substringAfter('=').trim())
                result[k] = v
            }
        }
        return result
    }
}
