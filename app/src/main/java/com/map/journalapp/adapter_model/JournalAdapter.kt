package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.map.journalapp.R

// Adapter class for the RecyclerView
class JournalAdapter(
    private val journalList: List<JournalEntry>,
    private val onJournalClick: (JournalEntry) -> Unit  // Callback for journal click
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        private val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        private val journalDate: TextView = itemView.findViewById(R.id.journalDate)
        private val tagChipGroup: ChipGroup = itemView.findViewById(R.id.tagChipGroup)

        fun bind(journalEntry: JournalEntry, onJournalClick: (JournalEntry) -> Unit) {
            journalTitle.text = journalEntry.title
            journalDescription.text = journalEntry.description
            journalDate.text = journalEntry.date

            // Clear existing chips before adding new ones
            tagChipGroup.removeAllViews()

            // Add tags as chips
            journalEntry.tags.forEach { tag ->
                val chip = Chip(itemView.context)
                chip.text = tag
                chip.isCheckable = false
                tagChipGroup.addView(chip)
            }

            // Set the click listener on the itemView
            itemView.setOnClickListener {
                onJournalClick(journalEntry)  // Trigger the callback when a journal is clicked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.journal_card, parent, false)
        return JournalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(journalList[position], onJournalClick)
    }

    override fun getItemCount(): Int = journalList.size
}

