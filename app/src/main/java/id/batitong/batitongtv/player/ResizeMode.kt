package id.batitong.batitongtv.player

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * 5 Mode Aspek Rasio Pemutar Video yang dipertahankan dari upstream (§6).
 */
enum class ResizeMode(val media3Mode: Int, val displayNameRes: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, id.batitong.batitongtv.R.string.resize_fit),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, id.batitong.batitongtv.R.string.resize_fill),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, id.batitong.batitongtv.R.string.resize_zoom),
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, id.batitong.batitongtv.R.string.resize_fixed_width),
    FIXED_HEIGHT(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, id.batitong.batitongtv.R.string.resize_fixed_height);

    fun next(): ResizeMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
