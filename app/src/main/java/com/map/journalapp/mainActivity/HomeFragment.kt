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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.write.JournalDetailFragment
import com.map.journalapp.write.ViewNoteFragment
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var journalAdapter: JournalAdapter
    private val journalEntries = mutableListOf<JournalEntry>()
    private val firestore = FirebaseFirestore.getInstance()
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLoading = false
    private var isLastPage = false

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

        // Setup infinite scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && !isLastPage && lastVisibleItemPosition == journalEntries.size - 1) {
                    loadJournals() // Load next page of journals
                }
            }
        })

        return view
    }

    override fun onResume() {
        super.onResume()
        loadJournals(initialLoad = true)
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

    private fun loadJournals(initialLoad: Boolean = false) {
        if (isLoading) return
        isLoading = true

        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return

        var query = firestore.collection("journals")
            .whereEqualTo("userId", userId)
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(40) // Limit to 40 per page for pagination

        if (!initialLoad && lastDocumentSnapshot != null) {
            query = query.startAfter(lastDocumentSnapshot)
        }

        query.get()
            .addOnSuccessListener { result ->
                if (initialLoad) {
                    journalEntries.clear()
                }

                if (result.isEmpty) {
                    isLastPage = true
                } else {
                    for (document in result) {
                        val journalId = document.id
                        val title = document.getString("title") ?: "No Title"
                        val imageUrl = document.getString("image_url")
                        val tagIds = document.get("tags") as? List<String> ?: emptyList()
                        val timestamp = document.getLong("created_at") ?: 0L
                        val formattedDate = formatTimestamp(timestamp)

                        // Fetch content for shortDescription and fullDescription
                        fetchJournalContent(journalId) { shortDescription, fullDescription ->
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
                    lastDocumentSnapshot = result.documents.lastOrNull()
                }

                isLoading = false
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                isLoading = false
                Toast.makeText(requireContext(), "Failed to load journals: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchJournalContent(
        journalId: String,
        callback: (shortDescription: String, fullDescription: String) -> Unit
    ) {
        firestore.collection("journals")
            .document(journalId)
            .collection("notes")
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val fullDescription = result.documents.firstOrNull()?.getString("content") ?: "No Notes Available"
                val shortDescription = getFirst20Words(fullDescription)
                callback(shortDescription, fullDescription)
            }
            .addOnFailureListener {
                callback("Click to view", "No Notes Available")
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
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
