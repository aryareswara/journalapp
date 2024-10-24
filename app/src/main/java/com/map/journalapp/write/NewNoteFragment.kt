package com.map.journalapp.write

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.databinding.FragmentNewNoteBinding
import java.io.InputStream
import java.util.*

class NewNoteFragment : Fragment() {

    private var _binding: FragmentNewNoteBinding? = null
    private val binding get() = _binding!!

    private val REQUEST_IMAGE_SELECT = 1002
    private var imageUri: Uri? = null
    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Journal ID and Title passed from FillJournalFragment
    private var journalId: String? = null
    private var journalTitle: String? = null
    private var journalTags: ArrayList<String>? = null  // To store tags

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the journal ID, title, and content from arguments
        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        journalTags = arguments?.getStringArrayList("journalTags")  // Get tags from arguments

        // Set the journal title in the TextView
        binding.journalTitleDisplay.text = journalTitle

        // Display the note content in the EditText
        val noteContent = arguments?.getString("noteContent")
        if (!noteContent.isNullOrEmpty()) {
            binding.journalContentInput.setText(noteContent)
        }

        // Display the tags under the title
        displayTags()

        // Handle adding an image to the note content
        binding.btnAddImage.setOnClickListener {
            selectImageForNote()
        }

        // Save note content
        binding.btnSave.setOnClickListener {
            saveNoteToFirestore()
        }
    }

    // Function to display tags
    private fun displayTags() {
        // Ensure journalTags is not null or empty
        if (journalTags != null && journalTags!!.isNotEmpty()) {
            for (tagId in journalTags!!) {
                // Fetch the tag name from Firestore based on tagId
                firestore.collection("tags").document(tagId).get()
                    .addOnSuccessListener { document ->
                        val tagName = document.getString("tagName")
                        if (!tagName.isNullOrEmpty()) {
                            // Create a Chip with the tag name and add it to the tagContainer
                            val chip = Chip(requireContext())
                            chip.text = tagName
                            chip.isClickable = false
                            binding.tagContainer.addView(chip)  // tagContainer is a LinearLayout
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Log the error for debugging
                        exception.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to load tag: $tagId", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            println("No tags to display")
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
            insertImageIntoNote()
        }
    }

    // Insert the image into the note's EditText
    private fun insertImageIntoNote() {
        if (imageUri != null) {
            try {
                // Open the input stream from the URI
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(imageUri!!)

                // Decode the image
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Check if the bitmap was loaded successfully
                if (bitmap != null) {
                    // Create an ImageSpan from the loaded image
                    val imageSpan = ImageSpan(requireContext(), bitmap)

                    // Get the current text from the EditText
                    val content = binding.journalContentInput.text

                    // Create a SpannableString from the existing content
                    val spannableString = SpannableString(content)

                    // Insert the ImageSpan at the current cursor position
                    val start = binding.journalContentInput.selectionStart
                    spannableString.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // Update the EditText with the SpannableString
                    binding.journalContentInput.setText(spannableString)
                    binding.journalContentInput.setSelection(start + 1) // Move cursor after the image
                } else {
                    Toast.makeText(requireContext(), "Failed to decode image", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveNoteToJournal(journalId: String, noteContent: String) {
        val noteData = hashMapOf(
            "content" to noteContent,
            "created_at" to System.currentTimeMillis()
        )
        firestore.collection("journals").document(journalId)
            .collection("notes")
            .add(noteData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Note added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add note", Toast.LENGTH_SHORT).show()
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

            // Log journalId for debugging
            println("Saving note to journal ID: $journalId")

            // Save the note to the notes subcollection under the specific journal
            firestore.collection("journals").document(journalId!!)
                .collection("notes")
                .add(noteData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    // Log the error for debugging
                    exception.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to save note: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            if (journalId == null) {
                Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please write something in the note", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
