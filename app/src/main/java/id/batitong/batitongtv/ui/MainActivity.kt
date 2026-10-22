package id.batitong.batitongtv.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import id.batitong.batitongtv.R
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.data.pref.PreferenceManager
import id.batitong.batitongtv.databinding.ActivityMainBinding
import id.batitong.batitongtv.player.Media3PlayerManager
import id.batitong.batitongtv.player.ResizeMode
import id.batitong.batitongtv.ui.adapter.CategoryAdapter
import id.batitong.batitongtv.ui.adapter.ChannelAdapter
import id.batitong.batitongtv.ui.dialog.SearchDialogFragment
import id.batitong.batitongtv.ui.dialog.SettingsDialogFragment
import id.batitong.batitongtv.ui.dialog.TrackSelectorDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Layar Utama BatitongTV:
 * - Struktur Layar 3-Panel Modern TV (Kategori + Saluran + Info/Preview)
 * - Pemutar Media3 Fullscreen dengan OSD Transparan
 * - Navigasi D-Pad Eksplisit dan Pemetaan Tombol Remote Fisik TV
 * - Picture-in-Picture & Pemutaran Saluran Terakhir Saat Booting/Start (§8 & §9)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var prefManager: PreferenceManager

    private var playerManager: Media3PlayerManager? = null
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private var currentResizeMode = ResizeMode.FIT
    private var isPlayerFullscreen = false
    private var isOsdVisible = false
    private val osdHandler = Handler(Looper.getMainLooper())
    private val osdHideRunnable = Runnable { hideOsd() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)
        setupPlayer()
        setupThreePanelUi()
        observeViewModel()
        setupOsdControls()
    }

    private fun setupPlayer() {
        playerManager = Media3PlayerManager(this, binding.playerView).apply {
            onPlaybackStateChanged = { isPlaying, isBuffering ->
                binding.viewPlayerOsd.osdLiveBadge.text = if (isBuffering) {
                    getString(R.string.status_buffering)
                } else {
                    "LIVE"
                }
                binding.viewPlayerOsd.btnOsdPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }

            onPlaybackError = { errorMsg, canRetry ->
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                if (canRetry) {
                    retryCurrent()
                }
            }
        }
    }

    private fun setupThreePanelUi() {
        // Adapter Kategori (Panel Kiri)
        categoryAdapter = CategoryAdapter(
            onCategorySelected = { category ->
                viewModel.selectCategory(category)
            },
            onCategoryFocused = { category ->
                viewModel.selectCategory(category)
            }
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = categoryAdapter

        // Adapter Saluran (Panel Tengah)
        channelAdapter = ChannelAdapter(
            onChannelClick = { channel ->
                startPlayback(channel)
            },
            onChannelLongClick = { channel ->
                // Long-press D-Pad / Tahan OK untuk favorit (§9)
                viewModel.toggleFavorite(channel)
                Toast.makeText(
                    this,
                    if (prefManager.isFavorite(channel.id)) "Ditambahkan ke Favorit" else "Dihapus dari Favorit",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onChannelFocused = { channel ->
                viewModel.selectPreviewChannel(channel)
            }
        )
        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = channelAdapter

        // Tombol Pencarian & Pengaturan Cepat
        binding.btnQuickSearch.setOnClickListener {
            openSearchDialog()
        }

        binding.btnQuickSettings.setOnClickListener {
            openSettingsDialog()
        }

        // Tombol Putar pada Panel Kanan
        binding.btnPlaySelectedChannel.setOnClickListener {
            val preview = viewModel.uiState.value.selectedPreviewChannel
            if (preview != null) {
                startPlayback(preview)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Status Loading
                binding.progressBarLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.tvLoadingStatus.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                // Banner Mode Offline
                binding.tvOfflineBanner.visibility = if (state.isOfflineMode) View.VISIBLE else View.GONE

                // Update Kategori & Saluran
                categoryAdapter.updateCategories(state.categories, state.activeCategory?.name)
                channelAdapter.updateChannels(state.activeChannels, state.favoriteChannelIds)

                binding.tvActiveCategoryTitle.text = state.activeCategory?.name ?: getString(R.string.category_all)
                binding.tvChannelCountBadge.text = "${state.activeChannels.size} Saluran"

                // Update Panel Kanan (Preview Saluran)
                state.selectedPreviewChannel?.let { preview ->
                    binding.tvPreviewTitle.text = preview.name
                    binding.tvPreviewCategory.text = preview.group
                    binding.tvPreviewFormatTag.text = preview.manifestType.name
                    binding.tvPreviewDrmTag.visibility = if (preview.hasDrm) View.VISIBLE else View.GONE

                    if (!preview.logoUrl.isNullOrBlank()) {
                        binding.ivPreviewLogo.load(preview.logoUrl) {
                            placeholder(R.drawable.ic_channel_placeholder)
                            error(R.drawable.ic_channel_placeholder)
                        }
                    } else {
                        binding.ivPreviewLogo.setImageResource(R.drawable.ic_channel_placeholder)
                    }
                }

                // Autoplay saluran terakhir saat startup (§9) jika belum ada saluran yang diputar
                if (!state.isLoading && state.currentlyPlayingChannel == null && prefManager.isAutoplayLastChannelEnabled) {
                    val lastUrl = prefManager.lastPlayedStreamUrl
                    if (!lastUrl.isNullOrBlank()) {
                        val lastCh = viewModel.findChannelByStreamUrl(lastUrl)
                        if (lastCh != null && !isPlayerFullscreen) {
                            startPlayback(lastCh)
                        }
                    }
                }
            }
        }
    }

    private fun startPlayback(channel: Channel) {
        viewModel.setCurrentlyPlayingChannel(channel)
        isPlayerFullscreen = true

        // Sembunyikan panel navigasi, tampilkan player
        binding.layoutMainThreePanel.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        // Siapkan info pada OSD
        binding.viewPlayerOsd.osdChannelTitle.text = channel.name
        binding.viewPlayerOsd.osdCategoryTag.text = channel.group
        binding.viewPlayerOsd.osdDrmTag.visibility = if (channel.hasDrm) View.VISIBLE else View.GONE
        if (!channel.logoUrl.isNullOrBlank()) {
            binding.viewPlayerOsd.osdChannelLogo.load(channel.logoUrl)
        }

        playerManager?.playChannel(channel)
        showOsd()
    }

    private fun setupOsdControls() {
        val osd = binding.viewPlayerOsd

        osd.btnOsdPlayPause.setOnClickListener {
            playerManager?.togglePlayPause()
            resetOsdTimer()
        }

        osd.btnOsdPrevChannel.setOnClickListener {
            viewModel.getPrevChannel()?.let { startPlayback(it) }
            resetOsdTimer()
        }

        osd.btnOsdNextChannel.setOnClickListener {
            viewModel.getNextChannel()?.let { startPlayback(it) }
            resetOsdTimer()
        }

        // Siklus 5 Mode Aspek Rasio (§6 & §9)
        osd.btnOsdAspectRatio.setOnClickListener {
            currentResizeMode = currentResizeMode.next()
            playerManager?.setResizeMode(currentResizeMode)
            Toast.makeText(this, currentResizeMode.displayNameRes, Toast.LENGTH_SHORT).show()
            resetOsdTimer()
        }

        // Pemilih Trek Audio & Subtitle (§6 & §9)
        osd.btnOsdTrackSelector.setOnClickListener {
            val audioTracks = playerManager?.getAudioTracks() ?: emptyList()
            if (audioTracks.isNotEmpty()) {
                TrackSelectorDialogFragment(audioTracks) { selectedTrack ->
                    playerManager?.selectTrack(selectedTrack)
                }.show(supportFragmentManager, "track_selector_dialog")
            } else {
                Toast.makeText(this, "Tidak ada pilihan trek audio tambahan.", Toast.LENGTH_SHORT).show()
            }
            resetOsdTimer()
        }

        // Picture-in-Picture (§6 & §9)
        osd.btnOsdPip.setOnClickListener {
            enterPipMode()
        }

        // Kembali ke daftar saluran
        osd.btnOsdChannelList.setOnClickListener {
            exitFullscreenToChannelList()
        }
    }

    private fun showOsd() {
        binding.viewPlayerOsd.root.visibility = View.VISIBLE
        isOsdVisible = true
        binding.viewPlayerOsd.btnOsdPlayPause.requestFocus()
        resetOsdTimer()
    }

    private fun hideOsd() {
        binding.viewPlayerOsd.root.visibility = View.GONE
        isOsdVisible = false
        osdHandler.removeCallbacks(osdHideRunnable)
    }

    private fun resetOsdTimer() {
        osdHandler.removeCallbacks(osdHideRunnable)
        osdHandler.postDelayed(osdHideRunnable, 5000) // Sembunyikan OSD otomatis setelah 5 detik
    }

    private fun exitFullscreenToChannelList() {
        hideOsd()
        isPlayerFullscreen = false
        binding.playerView.visibility = View.GONE
        binding.layoutMainThreePanel.visibility = View.VISIBLE
        binding.rvChannels.requestFocus()
    }

    /**
     * Pemetaan Tombol Fisik Remote TV & D-Pad Player Upstream (§6 & §8):
     * - DPAD_CENTER / ENTER: Tampilkan kontrol OSD saat fullscreen
     * - MEDIA_NEXT / CHANNEL_UP / DPAD_DOWN: Saluran berikutnya
     * - MEDIA_PREVIOUS / CHANNEL_DOWN / DPAD_UP: Saluran sebelumnya
     * - PAGE_UP / PAGE_DOWN: Ganti kategori saluran
     * - MENU: Buka dialog track selector
     * - MEDIA_PLAY / PAUSE / PLAY_PAUSE: Toggle pemutar video
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isPlayerFullscreen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!isOsdVisible) {
                        showOsd()
                        return true
                    }
                }

                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    viewModel.getNextChannel()?.let { startPlayback(it) }
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    viewModel.getPrevChannel()?.let { startPlayback(it) }
                    return true
                }

                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    viewModel.getNextCategory()?.let {
                        viewModel.selectCategory(it)
                        val firstCh = it.channels.firstOrNull()
                        if (firstCh != null) startPlayback(firstCh)
                    }
                    return true
                }

                KeyEvent.KEYCODE_PAGE_UP -> {
                    viewModel.getPrevCategory()?.let {
                        viewModel.selectCategory(it)
                        val firstCh = it.channels.firstOrNull()
                        if (firstCh != null) startPlayback(firstCh)
                    }
                    return true
                }

                KeyEvent.KEYCODE_MENU -> {
                    val audioTracks = playerManager?.getAudioTracks() ?: emptyList()
                    if (audioTracks.isNotEmpty()) {
                        TrackSelectorDialogFragment(audioTracks) { track ->
                            playerManager?.selectTrack(track)
                        }.show(supportFragmentManager, "track_selector_dialog")
                    }
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    playerManager?.togglePlayPause()
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (isOsdVisible) {
                        hideOsd()
                        return true
                    } else {
                        exitFullscreenToChannelList()
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Masuk Picture-in-Picture saat pengguna menekan tombol Home (onUserLeaveHint) (§6 & §9).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerFullscreen) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                hideOsd()
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideOsd()
            binding.layoutMainThreePanel.visibility = View.GONE
            binding.playerView.visibility = View.VISIBLE
        } else {
            if (isPlayerFullscreen) {
                binding.playerView.visibility = View.VISIBLE
            } else {
                binding.layoutMainThreePanel.visibility = View.VISIBLE
            }
        }
    }

    private fun openSearchDialog() {
        SearchDialogFragment(
            onSearchQuery = { query ->
                viewModel.searchChannels(query)
            },
            onChannelSelected = { channel ->
                startPlayback(channel)
            }
        ).show(supportFragmentManager, "search_dialog")
    }

    private fun openSettingsDialog() {
        SettingsDialogFragment(
            onSortChanged = { isAlpha ->
                viewModel.setAlphabeticalSort(isAlpha)
            },
            onClearCacheRequested = {
                viewModel.clearCache()
            }
        ).show(supportFragmentManager, "settings_dialog")
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            playerManager?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        osdHandler.removeCallbacks(osdHideRunnable)
        playerManager?.release()
    }
}
