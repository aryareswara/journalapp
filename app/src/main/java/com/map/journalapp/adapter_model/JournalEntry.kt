package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val tags: List<String>,
    val imageUrl: String? = null // Nullable image URL
)
