package com.map.journalapp.adapter_model

import com.google.firebase.Timestamp

data class Folder(
    val id: String = "",
    val fileName: String = "",
    val created_at: Timestamp = Timestamp.now(),
    val user_id: String = "",
    val isPasswordProtected: Boolean = false,
    val password: String? = null
)
