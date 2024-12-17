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
import com.google.firebase.firestore.FieldPath
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.write.FillJournalFragment
import com.map.journalapp.write.NewNoteFragment
import java.sql.Date
import java.text.SimpleDateFormat
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

        // initialize RecyclerView and set its layout manager
        val recyclerView: RecyclerView = view.findViewById(R.id.journalRecycle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // set up the adapter to handle journal entries and their clicks
        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            // open specific note when clicked
            openNoteFragment(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        // load the journal entries from Firestore
        loadJournals()

        // set up the floating action button to add a new journal entry
        val fab: FloatingActionButton = view.findViewById(R.id.newJournalButton)
        fab.setOnClickListener {
            // replace current fragment with the FillJournalFragment to create a new journal
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, FillJournalFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }

        return view
    }

    // opens the NewNoteFragment with data from the clicked journal entry
    private fun openNoteFragment(journalEntry: JournalEntry) {
        val newNoteFragment = NewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalEntry.id)
                putString("journalTitle", journalEntry.title)
                putString("noteContent", journalEntry.fullDescription)
            }
        }

        // replace current fragment with the NewNoteFragment to display the full note
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, newNoteFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    // fetch and load journal entries from Firestore
    private fun loadJournals() {
        val user = FirebaseAuth.getInstance().currentUser
        // if the user is not logged in, return
        val userId = user?.uid ?: return

        firestore.collection("journals")
            .whereEqualTo("userId", userId)
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                // clear previous entries before loading new ones
                journalEntries.clear()

                for (document in result) {
                    val journalId = document.id
                    val title = document.getString("title") ?: "No Title"
                    val imageUrl = document.getString("image_url")
                    val tagIds = document.get("tags") as? List<String> ?: listOf()

                    // fetch the first note for each journal entry
                    firestore.collection("journals")
                        .document(journalId)
                        .collection("notes")
                        .limit(1)
                        .get()
                        .addOnSuccessListener { noteResult ->
                            var description = "No Notes Available"
                            var fullDescription = description

                            // if note exists, get its content
                            if (noteResult.documents.isNotEmpty()) {
                                fullDescription = noteResult.documents[0].getString("content") ?: "No Notes Available"
                                description = getFirst20Words(fullDescription)
                            }

                            val timestamp = document.getLong("created_at") ?: 0L
                            val formattedDate = formatTimestamp(timestamp)

                            // fetch the tags for the journal entry
                            fetchTags(tagIds) { tags ->
                                val journalEntry = JournalEntry(
                                    journalId,
                                    title,
                                    description,
                                    formattedDate,
                                    tags,
                                    imageUrl,
                                    fullDescription
                                )
                                journalEntries.add(journalEntry)
                                journalAdapter.notifyDataSetChanged() // Notify adapter to update the RecyclerView
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to load journals: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // format Firestore timestamp to a human-readable date string
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }

    // fetch tag names based on tag IDs and pass them to the callback function
    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        val tags = mutableListOf<String>()

        if (tagIds.isEmpty()) {
            callback(tags)
            return
        }

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
                callback(tags)
            }
    }

    // return the first 20 words of the content, followed by '...' if there are more words
    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)
        return words.joinToString(" ") + if (words.size == 20) "..." else ""
    }
}