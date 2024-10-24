package com.map.journalapp.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.databinding.FragmentNewNoteBinding
import java.util.*

class NewNoteFragment : Fragment() {

    private var _binding: FragmentNewNoteBinding? = null
    private val binding get() = _binding!!

    private val REQUEST_IMAGE_SELECT = 1002
    private var imageUri: Uri? = null
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Journal ID passed from FillJournalFragment
    private var journalId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the journal ID from arguments
        journalId = arguments?.getString("journalId")

        // Add image to the note content
        binding.btnAddImage.setOnClickListener {
            selectImageForNote()
        }

        // Save note content
        binding.btnSave.setOnClickListener {
            saveNoteToFirestore()
        }
    }

    private fun selectImageForNote() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_SELECT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_SELECT && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            uploadImageAndInsertIntoNote()
        }
    }

    private fun uploadImageAndInsertIntoNote() {
        if (imageUri != null) {
            val storageRef = storage.reference.child("note_images/${UUID.randomUUID()}.jpg")
            val uploadTask = storageRef.putFile(imageUri!!)

            uploadTask.addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    // Insert the image URL into the note content (as a placeholder)
                    val currentText = binding.journalContentInput.text.toString()
                    binding.journalContentInput.setText("$currentText\n[Image: $imageUrl]")
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveNoteToFirestore() {
        val content = binding.journalContentInput.text.toString()

        if (content.isNotEmpty() && journalId != null) {
            // Prepare note data
            val noteData = hashMapOf(
                "content" to content,
                "created_at" to System.currentTimeMillis()
            )

            // Save the note to the notes subcollection under the specific journal
            firestore.collection("journals").document(journalId!!)
                .collection("notes")
                .add(noteData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to save note", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Please write something in the note", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

