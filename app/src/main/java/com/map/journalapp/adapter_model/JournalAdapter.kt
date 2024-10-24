package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.map.journalapp.R


// Adapter class for the RecyclerView
class JournalAdapter(
    private val journalEntries: List<JournalEntry>,
    private val onJournalClick: (JournalEntry) -> Unit
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    inner class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        val journalDate: TextView = itemView.findViewById(R.id.journalDate)
        val journalImage: ImageView = itemView.findViewById(R.id.journalImage)
        val tagChipGroup: ChipGroup = itemView.findViewById(R.id.tagChipGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.journal_card, parent, false)
        return JournalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        val journalEntry = journalEntries[position]

        holder.journalTitle.text = journalEntry.title
        holder.journalDescription.text = journalEntry.description
        holder.journalDate.text = journalEntry.createdAt

        // Check if the journal entry has an image
        if (journalEntry.imageUrl.isNullOrEmpty()) {
            holder.journalImage.visibility = View.GONE  // Hide ImageView if no image URL is available
        } else {
            holder.journalImage.visibility = View.VISIBLE
            // Load the image from the URL (if you're using an image loading library like Glide or Picasso)
            Glide.with(holder.itemView.context)  // Use holder.itemView.context instead of context
                .load(journalEntry.imageUrl)  // Load the image URL
                .into(holder.journalImage)  // Into the ImageView
        }

        // Handle tag chips
        holder.tagChipGroup.removeAllViews() // clear previous chips
        for (tag in journalEntry.tags) {
            val chip = Chip(holder.itemView.context)
            chip.text = tag
            holder.tagChipGroup.addView(chip)
        }

        holder.itemView.setOnClickListener {
            onJournalClick(journalEntry)
        }
    }


    override fun getItemCount(): Int {
        return journalEntries.size
    }
}


