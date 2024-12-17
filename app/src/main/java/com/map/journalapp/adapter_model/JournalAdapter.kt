package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.map.journalapp.R

// Adapter class for the RecyclerView
class JournalAdapter(
    private var journalEntries: List<JournalEntry>, // List of journal entries
    private val onJournalClick: (JournalEntry) -> Unit // Callback for click action
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    // ViewHolder to bind journal data with the UI elements
    inner class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        val journalDate: TextView = itemView.findViewById(R.id.journalDate)
        val journalImage: ImageView = itemView.findViewById(R.id.journalImage)
        val tagChipGroup: ChipGroup = itemView.findViewById(R.id.tagChipGroup)
    }

    // Create ViewHolder using the journal_card layout
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.journal_card, parent, false)
        return JournalViewHolder(view)
    }

    // Bind data from journalEntries to the ViewHolder
    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        val journalEntry = journalEntries[position]

        // Set journal title, description, and date
        holder.journalTitle.text = journalEntry.title
        holder.journalDescription.text = journalEntry.shortDescription
        holder.journalDate.text = journalEntry.createdAt

        // Load the journal image using Glide with placeholder
        if (journalEntry.imageUrl.isNullOrEmpty()) {
            holder.journalImage.visibility = View.GONE
        } else {
            holder.journalImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(journalEntry.imageUrl)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_folder) // Placeholder image
                        .error(R.drawable.image_person)       // Error image
                )
                .into(holder.journalImage)
        }

        // Dynamically add tags to the ChipGroup
        holder.tagChipGroup.removeAllViews() // Clear any existing chips
        journalEntry.tags.forEach { tag ->
            val chip = Chip(holder.itemView.context).apply {
                text = tag
                isClickable = false
                isCheckable = false
            }
            holder.tagChipGroup.addView(chip)
        }

        // Set click listener for the entire journal card
        holder.itemView.setOnClickListener {
            onJournalClick(journalEntry)
        }
    }

    // Return the number of journal entries
    override fun getItemCount(): Int = journalEntries.size

    // Function to update the data dynamically
    fun updateData(newEntries: List<JournalEntry>) {
        journalEntries = newEntries
        notifyDataSetChanged()
    }
}
