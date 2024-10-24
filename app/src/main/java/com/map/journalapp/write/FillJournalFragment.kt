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
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentFillJournalBinding
import java.util.*

class FillJournalFragment : Fragment() {

    private var _binding: FragmentFillJournalBinding? = null
    private val binding get() = _binding!!

    private val REQUEST_IMAGE_SELECT = 1001
    private var coverImageUri: Uri? = null

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val selectedTagIds = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFillJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load available tags from Firestore
        loadTags()

        // Select image for cover
        binding.selectImageButton.setOnClickListener {
            openImageSelector()
        }

        // Add tag button click listener to add tag from EditText to ChipGroup
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim() // Get text from input field
            if (newTag.isNotEmpty()) {
                addTagToChipGroup(newTag) // Add the tag to ChipGroup
                binding.tagInput.text.clear() // Clear the input field after adding
            } else {
                Toast.makeText(requireContext(), "Please enter a tag", Toast.LENGTH_SHORT).show()
            }
        }

        // Save journal to Firestore and redirect to NewNoteFragment
        binding.btnToStory.setOnClickListener {
            saveJournalToFirestoreAndRedirect()
        }
    }

    private fun openImageSelector() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_SELECT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_SELECT && resultCode == Activity.RESULT_OK) {
            coverImageUri = data?.data
            binding.journalCoverImage.setImageURI(coverImageUri)
        }
    }

    // Load available tags from Firestore and display them in the ChipGroup
    private fun loadTags() {
        firestore.collection("tags").get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagName = document.getString("tagName") ?: ""

                    addTagToChipGroup(tagName, document.id) // Load tags from Firestore
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to add a tag to the ChipGroup dynamically
    private fun addTagToChipGroup(tagName: String, tagId: String? = null) {
        val chip = Chip(requireContext())
        chip.text = tagName
        chip.isCheckable = true
        chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Add tag to selectedTagIds when checked
                selectedTagIds.add(tagId ?: UUID.randomUUID().toString())
            } else {
                // Remove tag from selectedTagIds when unchecked
                selectedTagIds.remove(tagId ?: UUID.randomUUID().toString())
            }
        }

        binding.tagChipGroup.addView(chip)
    }

    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()

        if (title.isNotEmpty() && selectedTagIds.isNotEmpty()) {
            // Upload cover image to Firebase Storage (if selected)
            if (coverImageUri != null) {
                uploadImageAndSaveData(title, selectedTagIds)
            } else {
                saveJournalDataAndRedirect(title, selectedTagIds, null)
            }
        } else {
            Toast.makeText(requireContext(), "Please enter a title and select tags", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageAndSaveData(title: String, tags: MutableList<String>) {
        val storageRef = storage.reference.child("journal_covers/${UUID.randomUUID()}.jpg")
        val uploadTask = storageRef.putFile(coverImageUri!!)

        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                val imageUrl = uri.toString()
                saveJournalDataAndRedirect(title, tags, imageUrl)
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveJournalDataAndRedirect(title: String, tags: MutableList<String>, imageUrl: String?) {
        // Create a new journal entry
        val journalData = hashMapOf(
            "title" to title,
            "tags" to tags,
            "coverImageUrl" to imageUrl,
            "created_at" to System.currentTimeMillis()
        )

        // Save the journal to Firestore
        firestore.collection("journals")
            .add(journalData)
            .addOnSuccessListener { journalRef ->
                Toast.makeText(requireContext(), "Journal saved!", Toast.LENGTH_SHORT).show()
                // Redirect to NewNoteFragment, passing the journal ID
                redirectToNewNoteFragment(journalRef.id)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save journal", Toast.LENGTH_SHORT).show()
            }
    }

    private fun redirectToNewNoteFragment(journalId: String) {
        // Create an instance of NewNoteFragment, passing the journalId as an argument
        val newNoteFragment = NewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalId)
            }
        }

        // Start FragmentTransaction
        val transaction: FragmentTransaction = requireActivity().supportFragmentManager.beginTransaction()

        // Replace the current fragment (FillJournalFragment) with NewNoteFragment
        transaction.replace(R.id.fragment_container, newNoteFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
