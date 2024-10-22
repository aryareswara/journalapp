package com.map.journalapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.databinding.JournalCardBinding

data class JournalEntry(val title: String, val description: String, val date: String)

class JournalAdapter(private val journalList: List<JournalEntry>) :
    RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    class JournalViewHolder(private val binding: JournalCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(journalEntry: JournalEntry) {
            binding.journalTitle.text = journalEntry.title
            binding.journalDescription.text = journalEntry.description
            binding.journalDate.text = journalEntry.date
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = JournalCardBinding.inflate(inflater, parent, false)
        return JournalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(journalList[position])
    }

    override fun getItemCount(): Int = journalList.size
}