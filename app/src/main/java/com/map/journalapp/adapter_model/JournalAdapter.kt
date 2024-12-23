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

class JournalAdapter(
    private var journalEntries: List<JournalEntry>,
    private val onJournalClick: (JournalEntry) -> Unit,
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    inner class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        val journalDate: TextView = itemView.findViewById(R.id.journalDate)
        val journalImage: ImageView = itemView.findViewById(R.id.journalImage)
        val tagChipGroup: ChipGroup = itemView.findViewById(R.id.tagChipGroup)

        init {
            // Normal click
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onJournalClick(journalEntries[position])
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.journal_card, parent, false)
        return JournalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        val journalEntry = journalEntries[position]
        // Bind data...
        holder.journalTitle.text = journalEntry.title
        holder.journalDescription.text = journalEntry.shortDescription
        holder.journalDate.text = journalEntry.createdAt.toString()
        if (journalEntry.imageUrl.isNullOrEmpty()) {
            holder.journalImage.visibility = View.GONE
        } else {
            holder.journalImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(journalEntry.imageUrl)
                .into(holder.journalImage)
        }
        // Bind tags to ChipGroup
        holder.tagChipGroup.removeAllViews()
        journalEntry.tags?.forEach { tag ->
            val chip = Chip(holder.itemView.context).apply {
                text = tag.toString()
                isClickable = false
            }
            holder.tagChipGroup.addView(chip)
        }
    }

    override fun getItemCount(): Int = journalEntries.size

    fun updateData(newEntries: List<JournalEntry>) {
        journalEntries = newEntries
        notifyDataSetChanged()
    }
}

