package com.map.journalapp.mainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.write.JournalDetailFragment
import com.map.journalapp.write.ViewNoteFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var journalAdapter: JournalAdapter
    private val journalEntries = mutableListOf<JournalEntry>()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.journalRecycle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            openViewNoteFragment(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        val fab: FloatingActionButton = view.findViewById(R.id.newJournalButton)
        fab.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, JournalDetailFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadJournals()
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

    private fun loadJournals() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return

        firestore.collection("journals")
            .whereEqualTo("userId", userId)
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                journalEntries.clear()

                if (result.isEmpty) {
                    Toast.makeText(requireContext(), "No journals found", Toast.LENGTH_SHORT).show()
                } else {
                    for (document in result) {
                        val journalId = document.id
                        val title = document.getString("title") ?: "No Title"
                        val imageUrl = document.getString("image_url")
                        val tagIds = document.get("tags") as? List<String> ?: emptyList()
                        val timestamp = document.getLong("created_at") ?: 0L
                        val formattedDate = formatTimestamp(timestamp)
                        val folder = document.getString("folder_id")

                        if (folder == null) {
                            // Fetch the most recent note for the journal
                            fetchMostRecentNote(journalId) { shortDescription, fullDescription ->
                                fetchTags(tagIds) { tagNames ->
                                    val journalEntry = JournalEntry(
                                        id = journalId,
                                        title = title,
                                        shortDescription = shortDescription,
                                        createdAt = formattedDate,
                                        tags = tagNames,
                                        imageUrl = imageUrl,
                                        fullDescription = fullDescription
                                    )
                                    journalEntries.add(journalEntry)
                                    journalAdapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to load journals: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchMostRecentNote(
        journalId: String,
        callback: (shortDescription: String, fullDescription: String) -> Unit
    ) {
        firestore.collection("journals")
            .document(journalId)
            .collection("notes")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val fullDescription = result.documents.firstOrNull()?.getString("content") ?: "No Notes Available"
                val shortDescription = getFirst20Words(fullDescription)
                callback(shortDescription, fullDescription)
            }
            .addOnFailureListener {
                callback("No Notes Available", "No Notes Available")
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        if (tagIds.isEmpty()) {
            callback(emptyList())
            return
        }

        firestore.collection("tags")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tagIds)
            .get()
            .addOnSuccessListener { result ->
                val tags = result.documents.mapNotNull { it.getString("tagName") }
                callback(tags)
            }
            .addOnFailureListener {
                callback(emptyList())
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)
        return words.joinToString(" ") + if (words.size == 20) "..." else ""
    }
}
