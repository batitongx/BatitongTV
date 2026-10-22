package id.batitong.batitongtv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.batitong.batitongtv.data.model.Category
import id.batitong.batitongtv.databinding.ItemCategoryTvBinding

/**
 * Adapter untuk Panel Kategori Saluran TV (Panel Kiri).
 */
class CategoryAdapter(
    private var categories: List<Category> = emptyList(),
    private var selectedCategoryName: String? = null,
    private val onCategorySelected: (Category) -> Unit,
    private val onCategoryFocused: ((Category) -> Unit)? = null
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun updateCategories(newCategories: List<Category>, activeName: String?) {
        this.categories = newCategories
        this.selectedCategoryName = activeName
        notifyDataSetChanged()
    }

    fun setSelectedCategory(activeName: String?) {
        this.selectedCategoryName = activeName
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryTvBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category, category.name == selectedCategoryName)
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(
        private val binding: ItemCategoryTvBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category, isSelected: Boolean) {
            binding.tvCategoryName.text = "${category.name} (${category.channelCount})"
            binding.tvCategoryName.isSelected = isSelected

            binding.root.setOnClickListener {
                selectedCategoryName = category.name
                notifyDataSetChanged()
                onCategorySelected(category)
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onCategoryFocused?.invoke(category)
                }
            }
        }
    }
}
