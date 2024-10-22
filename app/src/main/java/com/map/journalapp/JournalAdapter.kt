package com.map.journalapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Define a data class for the journal entry
data class JournalEntry(val title: String, val description: String, val date: String)

// Adapter class for the RecyclerView
class JournalAdapter(private val journalList: List<JournalEntry>) :
    RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    // ViewHolder class to hold and bind the journal card view
    class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val journalTitle: TextView = itemView.findViewById(R.id.journalTitle)
        private val journalDescription: TextView = itemView.findViewById(R.id.journalDescription)
        private val journalDate: TextView = itemView.findViewById(R.id.journalDate)

        fun bind(journalEntry: JournalEntry) {
            journalTitle.text = journalEntry.title
            journalDescription.text = journalEntry.description
            journalDate.text = journalEntry.date
        }
    }

    // Inflating the journal_card layout and returning the ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.journal_card, parent, false) // Inflate layout without binding
        return JournalViewHolder(view)
    }

    // Binding data to each journal card view
    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(journalList[position])
    }

    override fun getItemCount(): Int = journalList.size
}
