package com.hftx.bodyar

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClothingAdapter(
    private var items: List<ClothingItem>,
    private val onItemSelected: (ClothingItem) -> Unit,
    private val onItemLongPressed: ((ClothingItem) -> Unit)? = null
) : RecyclerView.Adapter<ClothingAdapter.ViewHolder>() {

    private var selectedId: String? = null

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val frame: android.view.View = view.findViewById(R.id.thumb_frame)
        val image: ImageView = view.findViewById(R.id.thumb_image)
        val label: TextView = view.findViewById(R.id.thumb_label)
    }

    fun submitList(newItems: List<ClothingItem>, newSelectedId: String?) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    fun setSelected(id: String?) {
        selectedId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        if (item.isCustom) {
            val bmp = BitmapFactory.decodeFile(item.filePath)
            if (bmp != null) holder.image.setImageBitmap(bmp) else holder.image.setImageResource(R.drawable.ic_close)
        } else {
            holder.image.setImageResource(item.drawableRes)
        }

        holder.label.text = item.displayName
        holder.frame.isSelected = item.id == selectedId

        holder.itemView.setOnClickListener {
            onItemSelected(item)
        }
        holder.itemView.setOnLongClickListener {
            if (item.isCustom && onItemLongPressed != null) {
                onItemLongPressed.invoke(item)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
