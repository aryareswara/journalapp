package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R

class FolderAdapter(private val folderList: List<String>, private val onFolderClick: (String) -> Unit) :
    RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folderName = folderList[position]
        holder.bind(folderName)
    }

    override fun getItemCount(): Int = folderList.size

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderButton: AppCompatButton = itemView.findViewById(R.id.folderButton)

        fun bind(folderName: String) {
            folderButton.text = folderName
            folderButton.setOnClickListener {
                onFolderClick(folderName)
            }
        }
    }
}
