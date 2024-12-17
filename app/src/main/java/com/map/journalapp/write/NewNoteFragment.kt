package com.map.journalapp.write

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentNewNoteBinding
import com.map.journalapp.mainActivity.HomeFragment
import java.util.*

class NewNoteFragment : Fragment() {

    // binding for the layout views
    private var _binding: FragmentNewNoteBinding? = null
    private val binding get() = _binding!!

    // firestore instance to interact with the database
    private val firestore = FirebaseFirestore.getInstance()

    // journal ID, Title, and Tags passed from the previous fragment
    private var journalId: String? = null
    private var journalTitle: String? = null
    // store tags for the journal
    private var journalTags: ArrayList<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // inflate the fragment layout using binding
        _binding = FragmentNewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get the journal ID, title, and tags passed from the previous fragment
        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        journalTags = arguments?.getStringArrayList("journalTags")  // Get tags from arguments

        // set the journal title in the TextView
        binding.journalTitleDisplay.text = journalTitle

        // display the existing note content in the EditText, if any
        val noteContent = arguments?.getString("noteContent")
        if (!noteContent.isNullOrEmpty()) {
            binding.journalContentInput.setText(noteContent)
        }

        // display the tags associated with the journal
        displayTags()

        // handle save button click to save the note content
        binding.btnSave.setOnClickListener {
            saveNoteToFirestore()
        }

        // handle delete button click to delete the note from Firestore
        binding.btnDelete.setOnClickListener {
            deleteNoteFromFirestore()
        }
    }

    // function to display tags associated with the journal
    private fun displayTags() {
        // ensure journalTags is not null or empty
        if (journalTags != null && journalTags!!.isNotEmpty()) {
            for (tagId in journalTags!!) {
                // fetch the tag name from Firestore using the tagId
                firestore.collection("tags").document(tagId).get()
                    .addOnSuccessListener { document ->
                        val tagName = document.getString("tagName")
                        if (!tagName.isNullOrEmpty()) {
                            // create a Chip for each tag and add it to the tag container
                            val chip = Chip(requireContext())
                            chip.text = tagName
                            chip.isClickable = false  // Make chips non-clickable
                            binding.tagContainer.addView(chip)  // Add chip to the container
                        }
                    }
                    .addOnFailureListener { exception ->
                        // log the error and show a Toast message if tag retrieval fails
                        exception.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to load tag: $tagId", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            println("No tags to display")
        }
    }

    // function to save a note to the journal's notes subcollection in Firestore
    fun saveNoteToJournal(journalId: String, noteContent: String) {
        val noteData = hashMapOf(
            "content" to noteContent,
            // record the creation timestamp
            "created_at" to System.currentTimeMillis()
        )
        firestore.collection("journals").document(journalId)
            .collection("notes")
            // add the note data to the Firestore database
            .add(noteData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Note added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add note", Toast.LENGTH_SHORT).show()
            }
    }

    // function to save the note content to Firestore
    private fun saveNoteToFirestore() {
        val content = binding.journalContentInput.text.toString()

        // check if the content is not empty and journalId is not null
        if (content.isNotEmpty() && journalId != null) {
            // prepare the note data to be saved
            val noteData = hashMapOf(
                "content" to content,
                "created_at" to System.currentTimeMillis()  // Record the creation timestamp
            )

            // log the journal ID for debugging purposes
            println("Saving note to journal ID: $journalId")

            // save the note data to Firestore under the corresponding journal ID
            firestore.collection("journals").document(journalId!!)
                .collection("notes")
                // add the note to the notes subcollection
                .add(noteData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()

                    // navigate back to HomeFragment after saving the note
                    val transaction = requireActivity().supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.fragment_container, HomeFragment())
                    transaction.addToBackStack(null)
                    transaction.commit()
                }
                .addOnFailureListener { exception ->
                    // log the error and show a Toast message if saving the note fails
                    exception.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to save note: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // show a message if the journalId is null or if the note content is empty
            if (journalId == null) {
                Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please write something in the note", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // function to delete the note from Firestore
    private fun deleteNoteFromFirestore() {
        if (journalId != null) {
            // delete the journal document itself
            firestore.collection("journals").document(journalId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Journal deleted successfully", Toast.LENGTH_SHORT).show()
                    // navigate back or refresh UI after deletion
                    requireActivity().supportFragmentManager.popBackStack()
                }
                .addOnFailureListener { exception ->
                    // show a message if deletion fails
                    Toast.makeText(requireContext(), "Failed to delete journal: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // show a message if journalId is null
            Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
        }
    }

    // cleanup the binding when the view is destroyed to avoid memory leaks
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
