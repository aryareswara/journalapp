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


// adapter class for the RecyclerView
class JournalAdapter(
    // journal entry that want to show
    private val journalEntries: List<JournalEntry>,

    // callback if the journal item clicked
    private val onJournalClick: (JournalEntry) -> Unit
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    // viewholder to connect element with the journal data
    inner class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        val journalDate: TextView = itemView.findViewById(R.id.journalDate)
        val journalImage: ImageView = itemView.findViewById(R.id.journalImage)
        val tagChipGroup: ChipGroup = itemView.findViewById(R.id.tagChipGroup)
    }

    // membuat ViewHolder baru dengan layout yang sesuai
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.journal_card, parent, false)
        return JournalViewHolder(view)
    }

    // mengikat data jurnal ke tampilan di ViewHolder
    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        val journalEntry = journalEntries[position]

        // menampilkan data jurnal di tampilan
        holder.journalTitle.text = journalEntry.title
        holder.journalDescription.text = journalEntry.shortDescription
        holder.journalDate.text = journalEntry.createdAt

        // menampilkan gambar jika tersedia
        if (journalEntry.imageUrl.isNullOrEmpty()) {
            holder.journalImage.visibility = View.GONE
        } else {
            holder.journalImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(journalEntry.imageUrl)
                .into(holder.journalImage)
        }

        // menambahkan tag sebagai chip di ChipGroup
        holder.tagChipGroup.removeAllViews()
        for (tag in journalEntry.tags) {
            val chip = Chip(holder.itemView.context)
            chip.text = tag
            holder.tagChipGroup.addView(chip)
        }

        // mengatur aksi ketika item diklik
        holder.itemView.setOnClickListener {
            onJournalClick(journalEntry)
        }
    }

    // mengembalikan jumlah entri jurnal
    override fun getItemCount(): Int = journalEntries.size
}



