package com.example.nextnotify.ui.gallery

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.nextnotify.databinding.ItemAppSelectionBinding

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable
)

class AppSelectionAdapter(
    private val onCheckedChanged: (InstalledAppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectionAdapter.AppViewHolder>() {

    private val items = mutableListOf<InstalledAppItem>()
    private var selectedPackages: Set<String> = emptySet()

    fun submitItems(newItems: List<InstalledAppItem>, selectedPackages: Set<String>) {
        items.clear()
        items.addAll(newItems)
        this.selectedPackages = selectedPackages
        notifyDataSetChanged()
    }

    fun updateSelection(selectedPackages: Set<String>) {
        this.selectedPackages = selectedPackages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position], selectedPackages.contains(items[position].packageName))
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(
        private val binding: ItemAppSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InstalledAppItem, isSelected: Boolean) {
            binding.imageAppIcon.setImageDrawable(item.icon)
            binding.textAppName.text = item.appName
            binding.textPackageName.text = item.packageName

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = isSelected
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onCheckedChanged(item, checked)
            }

            binding.root.setOnClickListener {
                val newValue = !binding.switchEnabled.isChecked
                binding.switchEnabled.isChecked = newValue
            }
        }
    }
}
