package id.batitong.batitongtv.data.pref

import android.content.Context
import android.content.SharedPreferences

/**
 * Pengelola preferensi aplikasi BatitongTV (§4 & §9).
 * HANYA menyimpan preferensi pengguna (favorit, saluran terakhir, setelan boot, urutan).
 * TIDAK PERNAH menyimpan URL playlist, token, session key, atau URL stream mentah (§4).
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "batitongtv_user_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_LAST_PLAYED_STREAM = "last_played_stream"
        private const val KEY_LAST_PLAYED_NAME = "last_played_name"
        private const val KEY_AUTOPLAY_LAST = "autoplay_last"
        private const val KEY_BOOT_STARTUP = "boot_startup"
        private const val KEY_SORT_ALPHABETICAL = "sort_alphabetical"
        private const val KEY_FAVORITES = "favorite_channels_set"
    }

    var lastPlayedStreamUrl: String?
        get() = prefs.getString(KEY_LAST_PLAYED_STREAM, null)
        set(value) = prefs.edit().putString(KEY_LAST_PLAYED_STREAM, value).apply()

    var lastPlayedChannelName: String?
        get() = prefs.getString(KEY_LAST_PLAYED_NAME, null)
        set(value) = prefs.edit().putString(KEY_LAST_PLAYED_NAME, value).apply()

    var isAutoplayLastChannelEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_LAST, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_LAST, value).apply()

    var isBootStartupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_STARTUP, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()

    var isSortAlphabetical: Boolean
        get() = prefs.getBoolean(KEY_SORT_ALPHABETICAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_ALPHABETICAL, value).apply()

    fun getFavoriteChannelIds(): MutableSet<String> {
        val rawSet = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return rawSet.toMutableSet()
    }

    fun isFavorite(channelId: String): Boolean {
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return favorites.contains(channelId)
    }

    fun toggleFavorite(channelId: String): Boolean {
        val favorites = getFavoriteChannelIds()
        val isNowFav = if (favorites.contains(channelId)) {
            favorites.remove(channelId)
            false
        } else {
            favorites.add(channelId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
        return isNowFav
    }
}
