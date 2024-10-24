package com.map.journalapp.adapter_model

data class JournalEntry(
    val id: String,
    val title: String,
    val shortDescription: String,  // Deskripsi dengan 20 kata pertama
    val createdAt: String,
    val tags: List<String>,
    val imageUrl: String?,
    val fullDescription: String  // Deskripsi lengkap untuk ditampilkan di halaman penuh
)

