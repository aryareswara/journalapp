package com.map.journalapp.adapter_model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R

/**
 * Adapter for selecting journals to add to a folder.
 * Displays only the title and associated tags of each journal.
 */
class JournalSelectionAdapter(
    private val journals: List<JournalEntry>
) : RecyclerView.Adapter<JournalSelectionAdapter.ViewHolder>() {

    private val selectedJournalIds = mutableSetOf<String>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.journalTitle)
        val tags: TextView = itemView.findViewById(R.id.journalTags)
        val checkbox: CheckBox = itemView.findViewById(R.id.journalCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.dialog_select_journals, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val journal = journals[position]
        holder.title.text = journal.title
        holder.tags.text = journal.tags.joinToString(", ")

        // Handle checkbox state
        holder.checkbox.isChecked = selectedJournalIds.contains(journal.id)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedJournalIds.add(journal.id)
            } else {
                selectedJournalIds.remove(journal.id)
            }
        }
    }

    override fun getItemCount(): Int = journals.size

    fun getSelectedJournalIds(): List<String> = selectedJournalIds.toList()
}
