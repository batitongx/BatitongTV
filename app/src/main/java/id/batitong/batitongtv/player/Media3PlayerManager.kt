package id.batitong.batitongtv.player

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.security.CertificatePinningConfig
import java.util.Locale

/**
 * Manajer Pemutar AndroidX Media3 resmi BatitongTV (§6).
 * Mengintegrasikan DataSource dinamis (Certificate Pinning first-party vs standar),
 * DRM Session Manager, auto-retry saat error, dan kontrol track audio/video/subtitle.
 */
class Media3PlayerManager(
    private val context: Context,
    private val playerView: PlayerView? = null
) {

    private val trackSelector = DefaultTrackSelector(context)
    private var exoPlayer: ExoPlayer? = null

    private var currentChannel: Channel? = null
    private var retryCount = 0
    private val maxRetries = 3

    var onPlaybackStateChanged: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null
    var onPlaybackError: ((errorMessage: String, canRetry: Boolean) -> Unit)? = null
    var onTracksChanged: ((tracks: Tracks) -> Unit)? = null

    val defaultUserAgent: String
        get() = "BatitongTV/1.0.0 (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500,  // minBufferMs
                15000, // maxBufferMs
                1000,  // bufferForPlaybackMs
                2000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        playerView?.player = exoPlayer

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isPlaying = exoPlayer?.isPlaying == true
                onPlaybackStateChanged?.invoke(isPlaying, isBuffering)

                if (playbackState == Player.STATE_READY) {
                    retryCount = 0 // Reset hitungan coba ulang saat berhasil memutar siaran
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val isBuffering = exoPlayer?.playbackState == Player.STATE_BUFFERING
                onPlaybackStateChanged?.invoke(isPlaying, isBuffering)
            }

            override fun onTracksChanged(tracks: Tracks) {
                this@Media3PlayerManager.onTracksChanged?.invoke(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }
        })
    }

    /**
     * Memutar siaran saluran dengan konfigurasi DataSource & DRM yang sesuai.
     */
    fun playChannel(channel: Channel) {
        currentChannel = channel
        val streamUrl = channel.streamUrl

        if (streamUrl.isBlank()) {
            onPlaybackError?.invoke("URL siaran tidak tersedia.", false)
            return
        }

        // Periksa dukungan kriptografi DRM hardware jika saluran dilindungi DRM
        if (channel.drm != null) {
            val isSupported = DrmSessionManagerFactory.isCryptoSchemeSupported(channel.drm)
            if (!isSupported) {
                onPlaybackError?.invoke(
                    "Format DRM (${channel.drm.schemeUuid}) tidak didukung oleh perangkat ini.",
                    false
                )
                return
            }
        }

        val dataSourceFactory = createDataSourceFactory(channel)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        // Pasang DrmSessionManager jika ada
        if (channel.drm != null) {
            val drmManager = DrmSessionManagerFactory.createDrmSessionManager(
                channel.drm,
                dataSourceFactory
            )
            mediaSourceFactory.setDrmSessionManagerProvider(DrmSessionManagerProvider { drmManager })
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    /**
     * Memilih DataSource yang tepat berdasarkan host (First-Party Pinning vs Pihak Ketiga vs RTMP).
     */
    private fun createDataSourceFactory(channel: Channel): DataSource.Factory {
        val streamUrl = channel.streamUrl.lowercase(Locale.ROOT)

        // Jalur Protokol RTMP
        if (streamUrl.startsWith("rtmp://") || streamUrl.startsWith("rtmps://")) {
            return RtmpDataSource.Factory()
        }

        val userAgent = channel.getUserAgent(defaultUserAgent)

        // Jalur Host First-Party (menggunakan OkHttp dengan Certificate Pinning SPKI)
        if (CertificatePinningConfig.isFirstPartyHost(channel.streamUrl)) {
            val pinnedClient = CertificatePinningConfig.createPinnedOkHttpClient()
            return OkHttpDataSource.Factory(pinnedClient)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(channel.headers)
        }

        // Jalur Host Pihak Ketiga / CDN Umum (DefaultHttpDataSource dengan toleransi redirect)
        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(channel.headers)
    }

    private fun handlePlayerError(error: PlaybackException) {
        if (retryCount < maxRetries && currentChannel != null) {
            retryCount++
            // Auto-retry otomatis pemutaran saluran
            playChannel(currentChannel!!)
        } else {
            onPlaybackError?.invoke("Gagal memuat siaran. Tekan OK untuk coba lagi.", true)
        }
    }

    fun retryCurrent() {
        retryCount = 0
        currentChannel?.let { playChannel(it) }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    /**
     * Mengatur rasio aspek PlayerView.
     */
    fun setResizeMode(mode: ResizeMode) {
        playerView?.resizeMode = mode.media3Mode
    }

    /**
     * Mendapatkan daftar trek audio saat ini.
     */
    fun getAudioTracks(): List<TrackItem> {
        val tracks = exoPlayer?.currentTracks ?: return emptyList()
        val list = mutableListOf<TrackItem>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Audio ${list.size + 1}"
                    list.add(
                        TrackItem(
                            groupIndex = group,
                            trackIndex = i,
                            name = label,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Memilih trek tertentu berdasarkan override.
     */
    fun selectTrack(item: TrackItem) {
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(item.groupIndex.mediaTrackGroup, item.trackIndex)
            )
            .build()
    }

    data class TrackItem(
        val groupIndex: Tracks.Group,
        val trackIndex: Int,
        val name: String,
        val isSelected: Boolean
    )
}
