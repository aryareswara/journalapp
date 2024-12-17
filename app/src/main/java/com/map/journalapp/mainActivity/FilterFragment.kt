package com.map.journalapp.mainActivity

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.write.ViewNoteFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilterFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var journalAdapter: JournalAdapter
    private val journalEntries = mutableListOf<JournalEntry>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_filter, container, false)

        firestore = FirebaseFirestore.getInstance()

        val recyclerView: RecyclerView = view.findViewById(R.id.filteredJournalRecycle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Pass the onJournalClick function to the adapter
        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            // Call openNoteFragment when a journal is clicked
            openViewNoteFragment(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        // Get the selected tag ID from arguments
        val selectedTagId = arguments?.getString("selectedTagId")
        Log.d("FilterFragment", "Selected Tag ID: $selectedTagId")  // Log to verify the selected tag ID

        // Load journals based on the selected tag ID
        if (selectedTagId != null) {
            loadJournalsByTagId(selectedTagId)
        } else {
            Toast.makeText(requireContext(), "No tag selected", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun loadJournalsByTagId(tagId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // Ensure the user is authenticated

        Log.d("FilterFragment", "Current User ID: $userId, Loading journals for tag ID: $tagId")

        // Fetch the tag name based on the tagId
        firestore.collection("tags").document(tagId).get()
            .addOnSuccessListener { documentSnapshot ->
                val tagName = documentSnapshot.getString("tagName")

                if (tagName != null) {
                    // Query journals where the tag ID is included in the 'tags' array in journals
                    firestore.collection("journals")
                        .whereEqualTo("userId", userId)  // Only retrieve journals for the authenticated user
                        .whereArrayContains("tags", tagId)  // Match the tag ID
                        .get()
                        .addOnSuccessListener { result ->
                            journalEntries.clear()  // Clear current entries

                            if (result.isEmpty) {
                                Log.d("FilterFragment", "No journals found for tag: $tagName")
                                Toast.makeText(requireContext(), "No journals found for tag: $tagName", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener  // Early return to avoid further processing
                            }

                            for (document in result) {
                                val journalId = document.id
                                val title = document.getString("title") ?: "No Title"
                                val imageUrl = document.getString("image_url")
                                val tagIds = document.get("tags") as? List<String> ?: listOf()

                                // Fetch the first note for the journal
                                firestore.collection("journals")
                                    .document(journalId)
                                    .collection("notes")
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { noteResult ->
                                        var description = "No Notes Available"
                                        var fullDescription = description  // Store full description here

                                        if (noteResult.documents.isNotEmpty()) {
                                            fullDescription = noteResult.documents[0].getString("content") ?: "No Notes Available"
                                            // Use getFirst20Words for the card display
                                            description = getFirst20Words(fullDescription)
                                        }

                                        val timestamp = document.getLong("created_at") ?: 0L
                                        val formattedDate = formatTimestamp(timestamp)

                                        // Fetch tag names based on the tag IDs in the journal
                                        fetchTags(tagIds) { tags ->
                                            val journalEntry = JournalEntry(
                                                id = journalId,
                                                title = title,
                                                shortDescription = description,  // Show only 20 words on card
                                                createdAt = formattedDate,
                                                tags = tags,  // Translated tag names
                                                imageUrl = imageUrl,
                                                fullDescription = fullDescription  // Store the full description
                                            )
                                            journalEntries.add(journalEntry)

                                            Log.d("FilterFragment", "Added journal entry: $title")
                                            journalAdapter.notifyDataSetChanged()
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("FilterFragment", "Error loading journals: ${exception.message}")
                            Toast.makeText(requireContext(), "Failed to load journals: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.e("FilterFragment", "Tag not found for ID: $tagId")
                    Toast.makeText(requireContext(), "Tag not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FilterFragment", "Error loading tag name: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to load tag name: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openViewNoteFragment(journalEntry: JournalEntry) {
        val viewNoteFragment = ViewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalEntry.id)
                putString("journalTitle", journalEntry.title)
                putString("fullDescription", journalEntry.fullDescription)
                putString("image_url", journalEntry.imageUrl)
                putStringArrayList("tags", ArrayList(journalEntry.tags))
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, viewNoteFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)  // Take only the first 20 words
        return words.joinToString(" ") + if (words.size == 20) "..." else ""  // Add "..." if there are more than 20 words
    }

    // Fetch tag names from Firestore based on tag IDs
    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        val tags = mutableListOf<String>()

        if (tagIds.isEmpty()) {
            callback(tags)
            return
        }

        // Fetch the tag names by their document references
        firestore.collection("tags")
            .whereIn(FieldPath.documentId(), tagIds)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagName = document.getString("tagName") ?: "Unknown"
                    tags.add(tagName)
                }
                callback(tags)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
                callback(tags) // Return empty tags in case of failure
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }
}
