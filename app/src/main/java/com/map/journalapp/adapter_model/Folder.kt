package com.map.journalapp.model

import com.google.firebase.Timestamp

/**
 * Data class representing a Folder.
 *
 * @param id The unique ID of the folder (Firestore document ID).
 * @param fileName The name of the folder.
 * @param created_at The timestamp when the folder was created.
 * @param user_id The ID of the user who owns the folder.
 * @param isPasswordProtected Boolean indicating if the folder is password-protected.
 * @param password The hashed password for the folder, if password-protected.
 */
data class Folder(
    val id: String = "",
    val fileName: String = "",
    val created_at: Timestamp = Timestamp.now(),
    val user_id: String = "",
    val isPasswordProtected: Boolean = false,
    val password: String? = null
)
