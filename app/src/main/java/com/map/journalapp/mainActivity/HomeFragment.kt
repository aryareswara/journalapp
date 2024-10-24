package com.map.journalapp.mainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        val recyclerView: RecyclerView = view.findViewById(R.id.journalRecycle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            openNoteFragment(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        loadJournals()

        val newJournalButton: LinearLayout = view.findViewById(R.id.newJournalButton)
        newJournalButton.setOnClickListener {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, FillJournalFragment())
            transaction.addToBackStack(null)
            transaction.commit()
        }

        return view
    }

    private fun openNoteFragment(journalEntry: JournalEntry) {
        val newNoteFragment = NewNoteFragment().apply {
            arguments = Bundle().apply {
                putString("journalId", journalEntry.id)
                putString("journalTitle", journalEntry.title)
                putString("noteContent", journalEntry.fullDescription)  // Pass the full note content
            }
        }

        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, newNoteFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun loadJournals() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // Ensure the user is authenticated

        firestore.collection("journals")
            .whereEqualTo("userId", userId)
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                journalEntries.clear()

                for (document in result) {
                    val journalId = document.id
                    val title = document.getString("title") ?: "No Title"
                    val imageUrl = document.getString("image_url")
                    val tagIds = document.get("tags") as? List<String> ?: listOf()

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

                            fetchTags(tagIds) { tags ->
                                val journalEntry = JournalEntry(
                                    journalId,
                                    title,
                                    description,  // Show only 20 words on card
                                    formattedDate,
                                    tags,
                                    imageUrl,
                                    fullDescription  // Pass the full description
                                )
                                journalEntries.add(journalEntry)
                                journalAdapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to load journals: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Function to format timestamp into a human-readable date
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())  // Adjust date format as needed
        val date = Date(timestamp)  // Convert milliseconds to a Date object
        return sdf.format(date)  // Return formatted date string
    }

    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        val tags = mutableListOf<String>()

        if (tagIds.isEmpty()) {
            callback(tags)
            return
        }

        // Get all tags by their document references
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
    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)  // Ambil hanya 20 kata pertama
        return words.joinToString(" ") + if (words.size == 20) "..." else ""  // Tambahkan "..." jika ada lebih dari 20 kata
    }
}
