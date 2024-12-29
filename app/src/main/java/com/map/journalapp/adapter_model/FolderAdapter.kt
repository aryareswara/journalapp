// com.map.journalapp.adapter_model.FolderAdapter.kt
package com.map.journalapp.adapter_model

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R

class FolderAdapter(
    private var folders: List<Folder>,
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    fun updateFolders(newFolders: List<Folder>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderIcon: ImageView = itemView.findViewById(R.id.folderIconImageView)
        val folderName: TextView = itemView.findViewById(R.id.folderNameTextView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFolderClick(folders[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.folderName.text = folder.fileName
        holder.folderIcon.setImageResource(R.drawable.ic_folder)

        // Log folder name for debugging
        Log.d("FOLDER_ADAPTER", "Binding folder: '${folder.fileName}'")
    }

    override fun getItemCount(): Int = folders.size
}
