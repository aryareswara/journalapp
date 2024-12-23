package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R

class FolderSelectionAdapter(
    private val folders: List<Folder>,
    private val onFolderSelected: (Folder) -> Unit
) : RecyclerView.Adapter<FolderSelectionAdapter.ViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderIcon: ImageView = itemView.findViewById(R.id.folderIconImageView)
        val folderName: TextView = itemView.findViewById(R.id.folderNameTextView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Mark this as selected
                    val oldPos = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)

                    // Callback
                    onFolderSelected(folders[position])
                }
            }
        }
    }

    // For convenience
    fun getSelectedFolder(): Folder? {
        return if (selectedPosition in folders.indices) folders[selectedPosition] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // We can reuse folder.xml or something similar
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.folderName.text = folder.fileName
        holder.folderIcon.setImageResource(R.drawable.ic_folder)

        // Optional: highlight if selected
        holder.itemView.isSelected = (position == selectedPosition)
    }

    override fun getItemCount() = folders.size
}
