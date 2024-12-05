package com.map.journalapp.mainActivity

import android.content.Intent
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
import com.map.journalapp.write.FillJournalActivity
import com.map.journalapp.write.NewNoteActivity
import java.text.SimpleDateFormat
import java.util.*

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
            openNoteActivity(journalEntry)
        }
        recyclerView.adapter = journalAdapter

        loadJournals()

        val newJournalButton: LinearLayout = view.findViewById(R.id.newJournalButton)
        newJournalButton.setOnClickListener {
            val intent = Intent(requireContext(), FillJournalActivity::class.java)
            startActivity(intent)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadJournals() // Ensure journals are reloaded when fragment resumes
    }

    private fun openNoteActivity(journalEntry: JournalEntry) {
        val intent = Intent(requireContext(), NewNoteActivity::class.java).apply {
            putExtra("journalId", journalEntry.id)
            putExtra("journalTitle", journalEntry.title)
            putExtra("noteContent", journalEntry.fullDescription)
        }
        startActivity(intent)
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
                            var fullDescription = description
                            var noteId: String? = null

                            if (noteResult.documents.isNotEmpty()) {
                                val noteDoc = noteResult.documents[0]
                                fullDescription = noteDoc.getString("content") ?: "No Notes Available"
                                description = getFirst20Words(fullDescription)
                                noteId = noteDoc.id
                            }

                            val timestamp = document.getLong("created_at") ?: 0L
                            val formattedDate = formatTimestamp(timestamp)

                            fetchTags(tagIds) { tags ->
                                val journalEntry = JournalEntry(
                                    journalId,
                                    title,
                                    description,
                                    formattedDate,
                                    tags,
                                    imageUrl,
                                    fullDescription,
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

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }

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

    private fun getFirst20Words(content: String): String {
        val words = content.split("\\s+".toRegex()).take(20)
        return words.joinToString(" ") + if (words.size == 20) "..." else ""
    }
}
