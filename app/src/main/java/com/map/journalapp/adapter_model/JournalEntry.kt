package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,
    val title: String,
    val shortDescription: String,
    val createdAt: String,
    val tags: List<String>,         // Make sure this is a List<String> (or ArrayList<String>)
    val imageUrl: String? = null,
    val fullDescription: String = "",
    val folderId: String? = null
)
