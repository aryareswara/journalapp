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

        // Add tag button click listener to add tag from EditText to ChipGroup and Firestore
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim() // Get text from input field
            if (newTag.isNotEmpty()) {
                saveTagToFirestore(newTag) // Save the tag to Firestore
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
        // Clear the current ChipGroup to avoid duplicate tags
        binding.tagChipGroup.removeAllViews()

        // Fetch tags from Firestore
        firestore.collection("tags").get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagName = document.getString("tagName") ?: ""

                    // Add tags from Firestore to ChipGroup
                    addTagToChipGroup(tagName, document.id)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to add a tag to Firestore
    private fun saveTagToFirestore(tagName: String) {
        // Create a new tag object
        val tagData = hashMapOf(
            "tagName" to tagName
        )

        // Save the tag to Firestore
        firestore.collection("tags")
            .add(tagData)
            .addOnSuccessListener {
                // Reload the tags to display the newly added tag
                loadTags()
                Toast.makeText(requireContext(), "Tag added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add tag", Toast.LENGTH_SHORT).show()
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

        // Add the chip to the ChipGroup
        binding.tagChipGroup.addView(chip)
    }

    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()

        if (title.isNotEmpty() && selectedTagIds.isNotEmpty()) {
            // Prepare journal data
            val journalData = hashMapOf(
                "title" to title,
                "tags" to selectedTagIds,  // Save selected tags with the journal
                "created_at" to System.currentTimeMillis()
            )

            // Save journal to Firestore
            firestore.collection("journals")
                .add(journalData)
                .addOnSuccessListener { documentReference ->
                    // Once journal is saved, navigate to NewNoteFragment and pass title, journalId, and tags
                    val newNoteFragment = NewNoteFragment()
                    val bundle = Bundle()
                    bundle.putString("journalId", documentReference.id)
                    bundle.putString("journalTitle", title)
                    bundle.putStringArrayList("journalTags", ArrayList(selectedTagIds))  // Pass the tags
                    newNoteFragment.arguments = bundle

                    // Perform fragment transaction
                    val transaction = requireActivity().supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.fragment_container, newNoteFragment)
                    transaction.addToBackStack(null)
                    transaction.commit()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to save journal", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Please enter a title and select tags", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
