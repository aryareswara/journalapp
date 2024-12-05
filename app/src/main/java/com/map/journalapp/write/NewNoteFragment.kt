package com.map.journalapp.write

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentNewNoteBinding
import com.map.journalapp.mainActivity.HomeFragment
import java.util.*

class NewNoteFragment : Fragment() {

    private var _binding: FragmentNewNoteBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var journalId: String? = null
    private var journalTitle: String? = null
    private var journalTags: ArrayList<String>? = null

    private val IMAGE_PICK_CODE = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        journalTags = arguments?.getStringArrayList("journalTags")

        binding.journalTitleDisplay.text = journalTitle

        val noteContent = arguments?.getString("noteContent")
        if (!noteContent.isNullOrEmpty()) {
            binding.journalContentInput.setText(noteContent)
        }

        displayTags()

        binding.btnSave.setOnClickListener { saveNoteToFirestore() }
        binding.btnDelete.setOnClickListener { deleteNoteFromFirestore() }
        binding.btnBold.setOnClickListener { applyBoldStyle() }
        binding.btnItalic.setOnClickListener { applyItalicStyle() }
        binding.btnUnderline.setOnClickListener { applyUnderlineStyle() }
        binding.btnAddImage.setOnClickListener { openGallery() }
    }

    private fun applyBoldStyle() {
        val spannable = SpannableStringBuilder(binding.journalContentInput.text)
        val start = binding.journalContentInput.selectionStart
        val end = binding.journalContentInput.selectionEnd
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.journalContentInput.text = spannable
    }

    private fun applyItalicStyle() {
        val spannable = SpannableStringBuilder(binding.journalContentInput.text)
        val start = binding.journalContentInput.selectionStart
        val end = binding.journalContentInput.selectionEnd
        spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.journalContentInput.text = spannable
    }

    private fun applyUnderlineStyle() {
        val spannable = SpannableStringBuilder(binding.journalContentInput.text)
        val start = binding.journalContentInput.selectionStart
        val end = binding.journalContentInput.selectionEnd
        spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.journalContentInput.text = spannable
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
            // Get the current cursor position
            val start = binding.journalContentInput.selectionStart
            val end = binding.journalContentInput.selectionEnd
            val textLength = binding.journalContentInput.text?.length ?: 0

            // Debug logs to understand the indices
            println("Text length: $textLength, Selection start: $start, Selection end: $end")

            // Ensure the selection start and end are within the valid range
            if (start >= 0 && end <= textLength && start <= end) {
                // Retrieve the original bitmap from the URI
                val originalBitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)

                // Target width for the image in dp (small icon size, e.g., 100dp)
                val targetWidthDp = 100
                val scale = requireContext().resources.displayMetrics.density
                val targetWidthPx = (targetWidthDp * scale).toInt()  // Convert dp to pixels

                // Calculate the target height while keeping the aspect ratio
                val scaledHeight = originalBitmap.height * targetWidthPx / originalBitmap.width
                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidthPx, scaledHeight, true)

                // Create an ImageSpan with the resized bitmap
                val imageSpan = ImageSpan(requireContext(), resizedBitmap)

                // Prepare SpannableStringBuilder for inserting the image
                val spannable = SpannableStringBuilder(binding.journalContentInput.text)

                // Insert a placeholder space for the image at the cursor position
                spannable.insert(start, " ")

                // Apply the ImageSpan at the current cursor position
                spannable.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Update the EditText with the modified spannable text
                binding.journalContentInput.text = spannable

                // Move the cursor after the inserted image
                binding.journalContentInput.setSelection(start + 1)
            } else {
                Toast.makeText(requireContext(), "Invalid cursor position for image insertion.", Toast.LENGTH_SHORT).show()
                println("Cursor position is out of bounds for the current text length.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to insert image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to display tags
    private fun displayTags() {
        if (journalTags != null && journalTags!!.isNotEmpty()) {
            for (tagId in journalTags!!) {
                firestore.collection("tags").document(tagId).get()
                    .addOnSuccessListener { document ->
                        val tagName = document.getString("tagName")
                        if (!tagName.isNullOrEmpty()) {
                            Log.d("NewNoteFragment", "Fetched Tag: $tagName")
                            val chip = Chip(requireContext())
                            chip.text = tagName
                            chip.isClickable = false
                            binding.tagContainer.addView(chip)
                            Log.d("NewNoteFragment", "Added Tag: $tagName to tagContainer")
                        } else {
                            Log.d("NewNoteFragment", "Tag Name is empty for tagId: $tagId")
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("NewNoteFragment", "Failed to load tag: ${exception.message}")
                        Toast.makeText(requireContext(), "Failed to load tag", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Log.d("NewNoteFragment", "No tags to display")
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

                    val transaction = requireActivity().supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.fragment_container, HomeFragment())
                    transaction.addToBackStack(null)
                    transaction.commit()
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

    private fun deleteNoteFromFirestore() {
        if (journalId != null) {
            // Delete the journal document itself
            firestore.collection("journals").document(journalId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Journal deleted successfully", Toast.LENGTH_SHORT).show()
                    // Navigate back or refresh UI after deletion
                    requireActivity().supportFragmentManager.popBackStack()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to delete journal: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
