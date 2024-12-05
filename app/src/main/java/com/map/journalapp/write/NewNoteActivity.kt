package com.map.journalapp.write

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.MainActivity
import com.map.journalapp.R
import com.map.journalapp.databinding.ActivityNewNoteBinding

class NewNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewNoteBinding

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var journalId: String? = null
    private var journalTitle: String? = null
    private var journalTags: ArrayList<String>? = null
    private var noteId: String? = null

    private val IMAGE_PICK_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        journalId = intent.getStringExtra("journalId")
        journalTitle = intent.getStringExtra("journalTitle")
        journalTags = intent.getStringArrayListExtra("journalTags")
        noteId = intent.getStringExtra("noteId")

        binding.journalTitleDisplay.text = journalTitle

        val noteContent = intent.getStringExtra("noteContent")
        if (!noteContent.isNullOrEmpty()) {
            loadContent(noteContent)
        }

        displayTags()

        binding.btnSave.setOnClickListener { saveNoteToFirestore() }
        binding.btnDelete.setOnClickListener { deleteNoteFromFirestore() }
        binding.btnAddImage.setOnClickListener { openGallery() }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            imageUri?.let { insertImageIntoContent(it) }
        }
    }

    private fun insertImageIntoContent(uri: Uri) {
        try {
            val storageRef = storage.reference.child("journal_images/${System.currentTimeMillis()}.jpg")
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        val start = binding.journalContentInput.selectionStart
                        val spannable = SpannableStringBuilder(binding.journalContentInput.text)
                        spannable.insert(start, "[image:$downloadUrl] ")
                        binding.journalContentInput.text = spannable
                        binding.journalContentInput.setSelection(start + "[image:$downloadUrl]".length)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to insert image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadContent(content: String) {
        val spannable = SpannableStringBuilder()
        val regex = "\\[image:(.*?)\\]".toRegex()
        var lastIndex = 0

        regex.findAll(content).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1

            spannable.append(content.substring(lastIndex, start))

            val imageUrl = matchResult.groupValues[1]
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.parse(imageUrl))
            val imageSpan = ImageSpan(this, bitmap)
            spannable.setSpan(imageSpan, spannable.length, spannable.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.append(" ")

            lastIndex = end
        }

        spannable.append(content.substring(lastIndex))
        binding.journalContentInput.text = spannable
    }

    private fun displayTags() {
        journalTags?.forEach { tagId ->
            firestore.collection("tags").document(tagId).get()
                .addOnSuccessListener { document ->
                    val tagName = document.getString("tagName")
                    if (!tagName.isNullOrEmpty()) {
                        val chip = Chip(this)
                        chip.text = tagName
                        chip.isClickable = false
                        chip.isCheckable = false
                        binding.tagContainer.addView(chip)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load tag", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveNoteToFirestore() {
        val content = binding.journalContentInput.text.toString()

        if (content.isNotEmpty() && journalId != null) {
            val noteData = mapOf(
                "content" to content,
                "created_at" to System.currentTimeMillis()
            )

            if (!noteId.isNullOrEmpty()) {
                firestore.collection("journals").document(journalId!!)
                    .collection("notes").document(noteId!!)
                    .update(noteData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Note updated!", Toast.LENGTH_SHORT).show()
                        navigateToHomeFragment()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update note", Toast.LENGTH_SHORT).show()
                    }
            } else {
                firestore.collection("journals").document(journalId!!)
                    .collection("notes")
                    .add(noteData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                        navigateToHomeFragment()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save note", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(this, "Please write something in the note", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteNoteFromFirestore() {
        if (!noteId.isNullOrEmpty() && journalId != null) {
            firestore.collection("journals").document(journalId!!)
                .collection("notes").document(noteId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                    navigateToHomeFragment()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete note", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Cannot delete this note", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToHomeFragment() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
