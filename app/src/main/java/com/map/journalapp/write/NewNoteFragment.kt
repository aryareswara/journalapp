package com.map.journalapp.write

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.databinding.FragmentNewNoteBinding
import java.util.*

class NewNoteFragment : Fragment() {

    private var _binding: FragmentNewNoteBinding? = null
    private val binding get() = _binding!!

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

        // Save note content
        binding.btnSave.setOnClickListener {
            saveNoteToFirestore()
        }

        // Handle delete note action
        binding.btnDelete.setOnClickListener {
            deleteNoteFromFirestore()
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

    private fun deleteNoteFromFirestore() {
        if (journalId != null) {
            // Hapus catatan yang ada di Firestore
            firestore.collection("journals").document(journalId!!)
                .collection("notes")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    // Cek apakah ada catatan yang ditemukan
                    if (!querySnapshot.isEmpty) {
                        // Hapus semua dokumen yang terkait dengan catatan ini
                        for (document in querySnapshot.documents) {
                            firestore.collection("journals")
                                .document(journalId!!)
                                .collection("notes")
                                .document(document.id)
                                .delete()
                                .addOnSuccessListener {
                                    Toast.makeText(requireContext(), "Note deleted successfully", Toast.LENGTH_SHORT).show()
                                    // Kembali ke layar sebelumnya atau perbarui UI setelah penghapusan
                                    requireActivity().supportFragmentManager.popBackStack()
                                }
                                .addOnFailureListener { exception ->
                                    // Menangani kegagalan penghapusan
                                    Toast.makeText(requireContext(), "Failed to delete note: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Toast.makeText(requireContext(), "No note found to delete", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    // Menangani kegagalan dalam pengambilan catatan
                    Toast.makeText(requireContext(), "Failed to find note: ${exception.message}", Toast.LENGTH_SHORT).show()
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
