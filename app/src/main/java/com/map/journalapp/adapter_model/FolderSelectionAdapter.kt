package com.map.journalapp.adapter_model

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R
import com.map.journalapp.model.Folder

/**
 * Adapter for selecting a folder from the list.
 *
 * @param folders List of folders to display.
 * @param onFolderSelected Lambda function to handle folder selection.
 */
class FolderSelectionAdapter(
    private val folders: List<Folder>,
    private val onFolderSelected: (Folder) -> Unit
) : RecyclerView.Adapter<FolderSelectionAdapter.ViewHolder>() {

    /**
     * ViewHolder class for folder selection items.
     *
     * @param itemView The view representing a single folder item.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderIcon: ImageView = itemView.findViewById(R.id.folderIconImageViewAddJournal)
        val folderName: TextView = itemView.findViewById(R.id.folderNameTextViewAddJournal)

        init {
            // Set click listener for the entire item view
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFolderSelected(folders[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflate the folder selection item layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folder_selection_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Bind folder data to the views
        val folder = folders[position]
        holder.folderName.text = folder.fileName

        // Set the folder icon
        holder.folderIcon.setImageResource(R.drawable.ic_folder)

        // Log binding for debugging
        Log.d("FolderSelectionAdapter", "Binding folder: ${folder.fileName} (ID: ${folder.id})")
    }

    override fun getItemCount(): Int = folders.size
}
