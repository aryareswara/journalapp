package com.map.journalapp.write

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

        // Load tags from Firestore when the fragment is loaded
        loadTagsFromFirestore()

        // Button to save journal and redirect to NewNoteFragment
        binding.btnToStory.setOnClickListener {
            saveJournalToFirestoreAndRedirect()
        }

        // Add a new tag when the Add Tag button is clicked
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim()
            if (newTag.isNotEmpty()) {
                saveTagToFirestore(newTag)
                binding.tagInput.text.clear()
            } else {
                Toast.makeText(requireContext(), "Enter a valid tag", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load tags from Firestore and display them in ChipGroup
    private fun loadTagsFromFirestore() {
        firestore.collection("tags").get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    // Dynamically add chips for each tag from the database
                    val chip = Chip(requireContext())
                    chip.text = tagName
                    chip.isCheckable = true

                    // Add or remove tag ID from selectedTagIds when the chip is checked/unchecked
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedTagIds.add(tagId)  // Add the tag ID
                        } else {
                            selectedTagIds.remove(tagId)  // Remove the tag ID
                        }
                    }

                    // Add chip to the ChipGroup
                    binding.tagChipGroup.addView(chip)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    // Save a new tag to the tags collection in Firestore
    private fun saveTagToFirestore(newTag: String) {
        val tagData = hashMapOf("tagName" to newTag)
        firestore.collection("tags")
            .add(tagData)
            .addOnSuccessListener { documentReference ->
                val tagId = documentReference.id
                selectedTagIds.add(tagId)  // Add the tag ID to selectedTagIds
                Toast.makeText(requireContext(), "Tag added: $newTag", Toast.LENGTH_SHORT).show()

                // Dynamically create a chip for the new tag
                val chip = Chip(requireContext())
                chip.text = newTag
                chip.isCheckable = true
                chip.isChecked = true

                // Add or remove tag ID when the chip is checked/unchecked
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTagIds.add(tagId)
                    } else {
                        selectedTagIds.remove(tagId)
                    }
                }

                // Add the new chip to the ChipGroup
                binding.tagChipGroup.addView(chip)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add tag", Toast.LENGTH_SHORT).show()
            }
    }


    // Function to save the journal and its tags to Firestore
    // Function to save the journal and its tags to Firestore
    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the current timestamp when creating the journal
        val journalData = hashMapOf(
            "title" to title,
            "created_at" to System.currentTimeMillis(),  // Save current timestamp
            "tags" to selectedTagIds  // Save selected tag IDs
        )

        // Save journal to Firestore
        firestore.collection("journals")
            .add(journalData)
            .addOnSuccessListener { documentReference ->
                val journalId = documentReference.id
                navigateToNewNoteFragment(journalId, title)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save journal", Toast.LENGTH_SHORT).show()
            }
    }


    // Navigate to NewNoteFragment after saving the journal
    private fun navigateToNewNoteFragment(journalId: String, title: String) {
        val newNoteFragment = NewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalId)
                putString("journalTitle", title)
                putStringArrayList("journalTags", ArrayList(selectedTagIds))  // Pass the selected tag IDs
            }
        }

        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, newNoteFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
