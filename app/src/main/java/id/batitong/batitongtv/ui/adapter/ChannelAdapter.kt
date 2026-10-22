package id.batitong.batitongtv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import id.batitong.batitongtv.R
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.databinding.ItemChannelTvBinding

/**
 * Adapter untuk Grid/List Saluran TV (Panel Tengah).
 */
class ChannelAdapter(
    private var channels: List<Channel> = emptyList(),
    private var favoriteChannelIds: Set<String> = emptySet(),
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelLongClick: (Channel) -> Unit,
    private val onChannelFocused: ((Channel) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    fun updateChannels(newChannels: List<Channel>, favorites: Set<String>) {
        this.channels = newChannels
        this.favoriteChannelIds = favorites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelTvBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        val isFav = favoriteChannelIds.contains(channel.id) || channel.isFavorite
        holder.bind(channel, isFav)
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(
        private val binding: ItemChannelTvBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel, isFavorite: Boolean) {
            binding.tvChannelTitle.text = channel.name
            binding.tvChannelSubtitle.text = channel.group

            // Memuat logo saluran secara asinkron dengan Coil
            if (!channel.logoUrl.isNullOrBlank()) {
                binding.ivChannelLogo.load(channel.logoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_channel_placeholder)
                    error(R.drawable.ic_channel_placeholder)
                }
            } else {
                binding.ivChannelLogo.setImageResource(R.drawable.ic_channel_placeholder)
            }

            // Indikator Bintang Favorit
            binding.ivFavoriteIcon.visibility = if (isFavorite) View.VISIBLE else View.GONE

            // Badge DRM
            binding.tvDrmBadge.visibility = if (channel.hasDrm) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onChannelClick(channel)
            }

            // Tahan OK / Long press untuk tambah/hapus favorit (§9)
            binding.root.setOnLongClickListener {
                onChannelLongClick(channel)
                true
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onChannelFocused?.invoke(channel)
                }
            }
        }
    }
}
