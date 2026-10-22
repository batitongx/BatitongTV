package id.batitong.batitongtv.data.model

/**
 * Peringatan terstruktur yang dicatat selama parsing tanpa menghentikan atau menggagalkan parsing playlist.
 */
data class ParsingWarning(
    val lineNumber: Int,
    val tag: String,
    val message: String,
    val rawContent: String? = null
)

/**
 * Hasil parsing playlist lengkap dengan daftar saluran, kategori yang dikelompokkan, dan peringatan terstruktur.
 */
data class PlaylistResult(
    val channels: List<Channel>,
    val categories: List<Category>,
    val warnings: List<ParsingWarning> = emptyList()
)
