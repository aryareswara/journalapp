package com.map.journalapp.mainActivity

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            openViewNoteFragment(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        val selectedTagId = arguments?.getString("selectedTagId")
        Log.d("FilterFragment", "Selected Tag ID: $selectedTagId")

        if (selectedTagId != null) {
            loadJournalsByTagId(selectedTagId)
        } else {
            Toast.makeText(requireContext(), "No tag selected", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun loadJournalsByTagId(tagId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return

        // First, fetch the name of the tag from Firestore
        firestore.collection("tags").document(tagId).get()
            .addOnSuccessListener { documentSnapshot ->
                val tagName = documentSnapshot.getString("tagName")
                if (tagName != null) {
                    // Show which tag is chosen
                    val tagHeaderTextView = view?.findViewById<TextView>(R.id.tagHeaderTextView)
                    tagHeaderTextView?.text = "Tags Name: $tagName"

                    // Query journals where 'tags' array contains this tagId
                    firestore.collection("journals")
                        .whereEqualTo("userId", userId)
                        .whereArrayContains("tags", tagId)
                        .get()
                        .addOnSuccessListener { result ->
                            journalEntries.clear()
                            if (result.isEmpty) {
                                Log.d("FilterFragment", "No journals found for tag: $tagName")
                                Toast.makeText(requireContext(),
                                    "No journals found for tag: $tagName",
                                    Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }
                            for (document in result) {
                                val journalId = document.id
                                val title = document.getString("title") ?: "No Title"
                                val imageUrl = document.getString("image_url")
                                val tagIds = document.get("tags") as? List<String> ?: emptyList()

                                firestore.collection("journals")
                                    .document(journalId)
                                    .collection("notes")
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { noteResult ->
                                        var description = "No Notes Available"
                                        var fullDescription = description

                                        if (noteResult.documents.isNotEmpty()) {
                                            fullDescription = noteResult.documents[0]
                                                .getString("content") ?: "No Notes Available"
                                            description = getFirst20Words(fullDescription)
                                        }

                                        val timestamp = document.getLong("created_at") ?: 0L
                                        val formattedDate = formatTimestamp(timestamp)

                                        // Convert the tagIds (doc-level) -> list of real tag names
                                        fetchTags(tagIds) { realTagNames ->
                                            val journalEntry = JournalEntry(
                                                id = journalId,
                                                title = title,
                                                shortDescription = description,
                                                createdAt = formattedDate,
                                                tags = realTagNames,   // store list
                                                imageUrl = imageUrl,
                                                fullDescription = fullDescription
                                            )
                                            journalEntries.add(journalEntry)
                                            journalAdapter.notifyDataSetChanged()
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("FilterFragment", "Error loading journals: ${exception.message}")
                            Toast.makeText(requireContext(),
                                "Failed to load journals: ${exception.message}",
                                Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.e("FilterFragment", "Tag not found for ID: $tagId")
                    Toast.makeText(requireContext(), "Tag not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FilterFragment", "Error loading tag name: ${exception.message}")
                Toast.makeText(requireContext(),
                    "Failed to load tag name: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun openViewNoteFragment(journalEntry: JournalEntry) {
        val viewNoteFragment = ViewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalEntry.id)
                putString("journalTitle", journalEntry.title)
                putString("fullDescription", journalEntry.fullDescription)
                putString("image_url", journalEntry.imageUrl)
                // Pass the tag list
                putStringArrayList("tags", ArrayList(journalEntry.tags))
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, viewNoteFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)
        return words.joinToString(" ") + if (words.size == 20) "..." else ""
    }

    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        if (tagIds.isEmpty()) {
            callback(emptyList())
            return
        }
        firestore.collection("tags")
            .whereIn(FieldPath.documentId(), tagIds)
            .get()
            .addOnSuccessListener { result ->
                val tagNames = result.documents.map { it.getString("tagName") ?: "Unknown" }
                callback(tagNames)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }
}
