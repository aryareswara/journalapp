package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,
    val title: String,
    val shortDescription: String,  // Plain text summary or first few items from JSON
    val createdAt: String,
    val tags: List<String>,
    val imageUrl: String?,  // Cover image or first image URL if applicable
    val fullDescription: String  // JSON string for rich content
)

