package id.batitong.batitongtv.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import id.batitong.batitongtv.data.model.DrmConfig
import java.nio.charset.StandardCharsets

/**
 * Pabrik DrmSessionManager resmi Media3 sesuai spesifikasi §6.
 */
object DrmSessionManagerFactory {

    /**
     * Memeriksa apakah skema DRM didukung oleh hardware perangkat Android.
     */
    fun isCryptoSchemeSupported(drmConfig: DrmConfig): Boolean {
        return FrameworkMediaDrm.isCryptoSchemeSupported(drmConfig.schemeUuid)
    }

    /**
     * Membangun DrmSessionManager berdasarkan model ternormalisasi DrmConfig.
     */
    fun createDrmSessionManager(
        drmConfig: DrmConfig,
        httpDataSourceFactory: DataSource.Factory
    ): DrmSessionManager {
        val uuid = drmConfig.schemeUuid

        val builder = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            // Multi-session aktif untuk Widevine & PlayReady, non-aktif untuk ClearKey
            .setMultiSession(uuid != C.CLEARKEY_UUID)

        when (drmConfig) {
            is DrmConfig.ClearKey -> {
                val jwkBytes = (drmConfig.jwkJson ?: "{}").toByteArray(StandardCharsets.UTF_8)
                val callback = LocalMediaDrmCallback(jwkBytes)
                return builder.build(callback)
            }

            is DrmConfig.LicenseServer -> {
                val callback = HttpMediaDrmCallback(drmConfig.url, httpDataSourceFactory)
                // Pasang seluruh license headers (G7 & G9)
                for ((key, value) in drmConfig.licenseHeaders) {
                    callback.setKeyRequestProperty(key, value)
                }
                return builder.build(callback)
            }
        }
    }
}
