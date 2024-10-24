package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,            // Journal ID
    val title: String,
    val description: String,   // 50 words from notes
    val date: String,
    val tags: List<String>     // List of tags
)
