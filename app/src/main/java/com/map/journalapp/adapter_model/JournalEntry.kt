package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,
    val title: String,
    // untuk 20 kata saja
    val shortDescription: String,
    val createdAt: String,
    val tags: List<String>,
    val imageUrl: String?,
    // untuk keseluruhan isi
    val fullDescription: String
)

