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
 * Adapter for displaying folders in the RecyclerView.
 * Each folder item shows an icon and the folder name.
 * Handles click and long-click events.
 */
class FolderAdapter(
    private var folders: List<Folder>,
    private val onFolderClick: (Folder) -> Unit,
    private val onFolderLongClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    /**
     * Updates the folder list and notifies the adapter.
     *
     * @param newFolders The new list of folders to display.
     */
    fun updateFolders(newFolders: List<Folder>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    /**
     * ViewHolder class for folder items.
     *
     * @param itemView The view representing a single folder item.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderIcon: ImageView = itemView.findViewById(R.id.folderIconImageView)
        val folderName: TextView = itemView.findViewById(R.id.folderNameTextView)

        init {
            // Set click listener for the entire item view
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFolderClick(folders[position])
                }
            }

            // Set long-click listener for the entire item view
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFolderLongClick(folders[position])
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflate the folder item layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Bind folder data to the views
        val folder = folders[position]
        holder.folderName.text = folder.fileName

        // Set the folder icon (assuming ic_folder is a vector drawable)
        holder.folderIcon.setImageResource(R.drawable.ic_folder)

        // Log binding for debugging
        Log.d("FolderAdapter", "Binding folder: ${folder.fileName} (ID: ${folder.id})")
    }

    override fun getItemCount(): Int = folders.size
}
