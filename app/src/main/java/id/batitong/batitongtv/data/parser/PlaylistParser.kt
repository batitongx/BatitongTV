package id.batitong.batitongtv.data.parser

import id.batitong.batitongtv.data.model.ParsingWarning
import id.batitong.batitongtv.data.model.PlaylistResult

/**
 * Entry point tunggal untuk parsing playlist BatitongTV.
 * Mendeteksi format secara cerdas berdasarkan ISI dokumen (bukan ekstensi URL):
 * - Dimulai dengan '{' atau '[' -> JSON format lama (LegacyJsonParser)
 * - Mengandung '#EXTM3U' atau '#EXTINF' -> M3U extended (M3uParser)
 */
class PlaylistParser(
    private val m3uParser: M3uParser = M3uParser(),
    private val jsonParser: LegacyJsonParser = LegacyJsonParser()
) {

    fun parse(content: String): PlaylistResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return PlaylistResult(
                channels = emptyList(),
                categories = emptyList(),
                warnings = listOf(
                    ParsingWarning(
                        lineNumber = 0,
                        tag = "EMPTY",
                        message = "Konten playlist kosong."
                    )
                )
            )
        }

        // Deteksi format berdasarkan isi
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            jsonParser.parse(trimmed)
        } else if (trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF")) {
            m3uParser.parse(trimmed)
        } else {
            // Coba parsing sebagai M3U secara toleran
            m3uParser.parse(trimmed)
        }
    }
}
