package id.batitong.batitongtv.data.model

import java.io.Serializable

/**
 * Model Kategori Saluran.
 */
data class Category(
    val name: String,
    val channels: List<Channel> = emptyList(),
    val isFavoriteGroup: Boolean = false
) : Serializable {

    val channelCount: Int
        get() = channels.size
}
