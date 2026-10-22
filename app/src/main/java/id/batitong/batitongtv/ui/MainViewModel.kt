package id.batitong.batitongtv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.batitong.batitongtv.R
import id.batitong.batitongtv.data.model.Category
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.data.pref.PreferenceManager
import id.batitong.batitongtv.security.EncryptedCacheManager
import id.batitong.batitongtv.security.GatewayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class MainUiState(
    val categories: List<Category> = emptyList(),
    val activeCategory: Category? = null,
    val activeChannels: List<Channel> = emptyList(),
    val selectedPreviewChannel: Channel? = null,
    val currentlyPlayingChannel: Channel? = null,
    val favoriteChannelIds: Set<String> = emptySet(),
    val isOfflineMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    private val gatewayClient = GatewayClient(application)
    private val cacheManager = EncryptedCacheManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var allChannels: List<Channel> = emptyList()
    private var rawCategories: List<Category> = emptyList()

    init {
        _uiState.value = _uiState.value.copy(
            favoriteChannelIds = prefManager.getFavoriteChannelIds()
        )
        loadPlaylist()
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = gatewayClient.fetchPlaylist()
            result.onSuccess { playlistResult ->
                allChannels = playlistResult.channels
                rawCategories = playlistResult.categories

                // Cek apakah data berasal dari cache offline
                val isOffline = !gatewayClient.isTokenValid && cacheManager.hasValidCache()

                rebuildCategoriesWithFavorites(isOffline)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = getApplication<Application>().getString(R.string.error_playlist_load_generic)
                )
            }
        }
    }

    /**
     * Membangun kembali daftar kategori dengan menjamin kategori "Favorit" SELALU di posisi teratas (§9).
     */
    private fun rebuildCategoriesWithFavorites(isOffline: Boolean) {
        val favIds = prefManager.getFavoriteChannelIds()
        val favChannels = allChannels.filter { favIds.contains(it.id) }

        val context = getApplication<Application>()
        val favoriteCategoryName = context.getString(R.string.category_favorites)

        val updatedCategoryList = mutableListOf<Category>()

        // 1. Kategori Favorit SELALU di urutan teratas (§9)
        updatedCategoryList.add(
            Category(
                name = favoriteCategoryName,
                channels = favChannels,
                isFavoriteGroup = true
            )
        )

        // 2. Kategori "Semua Saluran"
        val allCategoryName = context.getString(R.string.category_all)
        updatedCategoryList.add(
            Category(
                name = allCategoryName,
                channels = allChannels,
                isFavoriteGroup = false
            )
        )

        // 3. Kategori dari dokumen playlist
        for (cat in rawCategories) {
            if (cat.name != favoriteCategoryName && cat.name != allCategoryName) {
                updatedCategoryList.add(cat)
            }
        }

        // Terapkan pengurutan abjad jika diaktifkan di preferensi
        val isAlpha = prefManager.isSortAlphabetical
        val sortedCategories = if (isAlpha) {
            // Favorit & Semua Saluran tetap di atas, sisanya diurutkan A-Z
            listOf(updatedCategoryList[0], updatedCategoryList[1]) +
                    updatedCategoryList.drop(2).sortedBy { it.name.lowercase(Locale.ROOT) }
        } else {
            updatedCategoryList
        }

        val initialActiveCategory = sortedCategories.firstOrNull { it.channels.isNotEmpty() }
            ?: sortedCategories.firstOrNull()

        var initialChannels = initialActiveCategory?.channels ?: emptyList()
        if (isAlpha) {
            initialChannels = initialChannels.sortedBy { it.name.lowercase(Locale.ROOT) }
        }

        val initialPreview = initialChannels.firstOrNull()

        _uiState.value = _uiState.value.copy(
            categories = sortedCategories,
            activeCategory = initialActiveCategory,
            activeChannels = initialChannels,
            selectedPreviewChannel = initialPreview,
            favoriteChannelIds = favIds,
            isOfflineMode = isOffline,
            isLoading = false
        )
    }

    fun selectCategory(category: Category) {
        var channels = category.channels
        if (prefManager.isSortAlphabetical) {
            channels = channels.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
        _uiState.value = _uiState.value.copy(
            activeCategory = category,
            activeChannels = channels,
            selectedPreviewChannel = channels.firstOrNull()
        )
    }

    fun selectPreviewChannel(channel: Channel) {
        _uiState.value = _uiState.value.copy(
            selectedPreviewChannel = channel
        )
    }

    fun setCurrentlyPlayingChannel(channel: Channel?) {
        _uiState.value = _uiState.value.copy(
            currentlyPlayingChannel = channel
        )
        channel?.let {
            // Simpan saluran terakhir yang ditonton untuk autoplay saat startup (§9)
            prefManager.lastPlayedStreamUrl = it.streamUrl
            prefManager.lastPlayedChannelName = it.name
        }
    }

    /**
     * Menambah atau menghapus saluran dari daftar favorit dengan tahan OK/Enter (§9).
     */
    fun toggleFavorite(channel: Channel) {
        prefManager.toggleFavorite(channel.id)
        val updatedFavs = prefManager.getFavoriteChannelIds()
        _uiState.value = _uiState.value.copy(favoriteChannelIds = updatedFavs)

        // Perbarui kategori favorit secara instan
        rebuildCategoriesWithFavorites(_uiState.value.isOfflineMode)
    }

    fun searchChannels(query: String): List<Channel> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.trim().lowercase(Locale.ROOT)
        return allChannels.filter {
            it.name.lowercase(Locale.ROOT).contains(lowerQuery) ||
                    it.group.lowercase(Locale.ROOT).contains(lowerQuery)
        }
    }

    fun setAlphabeticalSort(enabled: Boolean) {
        prefManager.isSortAlphabetical = enabled
        rebuildCategoriesWithFavorites(_uiState.value.isOfflineMode)
    }

    fun clearCache() {
        cacheManager.clearCache()
    }

    fun getNextChannel(): Channel? {
        val current = _uiState.value.currentlyPlayingChannel ?: return null
        val list = _uiState.value.activeChannels
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx != -1 && list.isNotEmpty()) {
            val nextIdx = (idx + 1) % list.size
            return list[nextIdx]
        }
        return null
    }

    fun getPrevChannel(): Channel? {
        val current = _uiState.value.currentlyPlayingChannel ?: return null
        val list = _uiState.value.activeChannels
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx != -1 && list.isNotEmpty()) {
            val prevIdx = if (idx - 1 < 0) list.size - 1 else idx - 1
            return list[prevIdx]
        }
        return null
    }

    fun getNextCategory(): Category? {
        val current = _uiState.value.activeCategory ?: return null
        val list = _uiState.value.categories
        val idx = list.indexOfFirst { it.name == current.name }
        if (idx != -1 && list.isNotEmpty()) {
            val nextIdx = (idx + 1) % list.size
            return list[nextIdx]
        }
        return null
    }

    fun getPrevCategory(): Category? {
        val current = _uiState.value.activeCategory ?: return null
        val list = _uiState.value.categories
        val idx = list.indexOfFirst { it.name == current.name }
        if (idx != -1 && list.isNotEmpty()) {
            val prevIdx = if (idx - 1 < 0) list.size - 1 else idx - 1
            return list[prevIdx]
        }
        return null
    }

    fun findChannelByStreamUrl(url: String): Channel? {
        return allChannels.find { it.streamUrl == url }
    }
}
