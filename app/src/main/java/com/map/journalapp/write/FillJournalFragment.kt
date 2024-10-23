package com.map.journalapp.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentFillJournalBinding
import com.map.journalapp.write.NewNoteFragment
import java.util.*

class FillJournalFragment : Fragment() {

    private var _binding: FragmentFillJournalBinding? = null
    private val binding get() = _binding!!

    private val REQUEST_IMAGE_SELECT = 1001
    private var coverImageUri: Uri? = null

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val selectedTags = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFillJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Select image for cover
        binding.selectImageButton.setOnClickListener {
            openImageSelector()
        }
        // Add tag when the button is clicked
        binding.addTagButton.setOnClickListener {
            addTagToContainer()
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
    // Function to add tags
    private fun addTagToContainer() {
        val tagText = binding.tagInput.text.toString().trim()

        if (tagText.isNotEmpty()) {
            // Add tag to the selectedTags list
            selectedTags.add(tagText)

            // Create a new TextView for the tag
            val tagView = TextView(requireContext())
            tagView.text = tagText
            tagView.setPadding(8, 8, 8, 8)
            tagView.setBackgroundResource(R.drawable.rounded_tag_background) // Optional: add a rounded background drawable if you like
            tagView.textSize = 16f

            // Add the TextView to the tagContainer
            binding.tagContainer.addView(tagView)

            // Clear the input field after adding the tag
            binding.tagInput.text.clear()
        } else {
            Toast.makeText(requireContext(), "Please enter a tag", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()
        val tags = arrayListOf<String>() // You can collect selected tags here
        tags.add("ExampleTag") // Example, replace with your tag input

        if (title.isNotEmpty()) {
            // Upload cover image to Firebase Storage (if selected)
            if (coverImageUri != null) {
                uploadImageAndSaveData(title, tags)
            } else {
                saveJournalDataAndRedirect(title, tags, null)
            }
        } else {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageAndSaveData(title: String, tags: ArrayList<String>) {
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

    private fun saveJournalDataAndRedirect(title: String, tags: ArrayList<String>, imageUrl: String?) {
        // Create a new journal entry
        val journalData = hashMapOf(
            "title" to title,
            "tags" to tags,
            "coverImageUrl" to imageUrl,
            "created_at" to System.currentTimeMillis()
        )

        // Save data to Firestore
        firestore.collection("journals")
            .add(journalData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Journal saved!", Toast.LENGTH_SHORT).show()
                // Redirect to NewNoteFragment after saving
                redirectToNewNoteFragment()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save journal", Toast.LENGTH_SHORT).show()
            }
    }

    private fun redirectToNewNoteFragment() {
        // Create an instance of NewNoteFragment
        val newNoteFragment = NewNoteFragment()

        // Start FragmentTransaction
        val transaction: FragmentTransaction = requireActivity().supportFragmentManager.beginTransaction()

        // Replace the current fragment (FillJournalFragment) with NewNoteFragment
        transaction.replace(R.id.fragment_container, newNoteFragment) // Make sure fragment_container is the correct ID in your activity layout
        transaction.addToBackStack(null) // Optional: add this to the back stack to allow users to navigate back
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
