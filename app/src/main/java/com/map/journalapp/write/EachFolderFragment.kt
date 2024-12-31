package com.map.journalapp.write

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.adapter_model.Folder
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.databinding.FragmentEachFolderBinding

class EachFolderFragment : Fragment() {

    private var _binding: FragmentEachFolderBinding? = null
    private val binding get() = _binding!!

    private var folderId: String? = null
    private lateinit var firestore: FirebaseFirestore

    private lateinit var journalAdapter: JournalAdapter
    private val journalEntries = mutableListOf<JournalEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderId = it.getString(ARG_FOLDER_ID)
        }
        firestore = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEachFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Setup RecyclerView
        binding.recyclerEachFolder.layoutManager = LinearLayoutManager(requireContext())
        journalAdapter = JournalAdapter(journalEntries) { journalEntry ->
            openViewNoteFragment(journalEntry) // Open ViewNoteFragment on journal click
        }
        binding.recyclerEachFolder.adapter = journalAdapter

        if (!folderId.isNullOrEmpty()) {
            fetchFolderName(folderId!!) // Sets folder name in TextView
            loadJournalsInFolder(folderId!!)
        }
    }

    /**
     * Opens the ViewNoteFragment with the selected journal details.
     */
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

    /**
     * Fetches the 'fileName' from the 'folders' collection.
     */
    private fun fetchFolderName(folderId: String) {
        firestore.collection("folders")
            .document(folderId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val folderObj = doc.toObject(Folder::class.java)
                    val folderName = folderObj?.fileName ?: "Unknown Folder"
                    binding.textViewFolderName.text = folderName
                } else {
                    binding.textViewFolderName.text = "(Folder not found)"
                }
            }
            .addOnFailureListener {
                binding.textViewFolderName.text = "(Error loading folder name)"
            }
    }

    /**
     * Loads all journals that have folder_id == folderId.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadJournalsInFolder(folderId: String) {
        firestore.collection("journals")
            .whereEqualTo("folder_id", folderId)
            .get()
            .addOnSuccessListener { result ->
                journalEntries.clear()
                if (result.isEmpty) {
                    journalAdapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "No journals in this folder", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                for (doc in result) {
                    val journalId = doc.id
                    val title = doc.getString("title") ?: "No Title"
                    val createdAt = doc.getLong("created_at") ?: 0L
                    val imageUrl = doc.getString("image_url")
                    val tagIds = doc.get("tags") as? List<String> ?: emptyList()

                    // Fetch the first note subcollection
                    firestore.collection("journals")
                        .document(journalId)
                        .collection("notes")
                        .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { noteSnap ->
                            var shortDesc = "No Notes Available"
                            var fullDesc = shortDesc
                            if (noteSnap.documents.isNotEmpty()) {
                                fullDesc = noteSnap.documents[0].getString("content") ?: "No Notes Available"
                                shortDesc = getFirst20Words(fullDesc)
                            }

                            // Convert createdAt to a readable date
                            val dateStr = android.text.format.DateFormat.getMediumDateFormat(requireContext())
                                .format(java.util.Date(createdAt))

                            // Convert tagIds to real tag names
                            fetchTagNames(tagIds) { realTagNames ->
                                val journalEntry = JournalEntry(
                                    id = journalId,
                                    title = title,
                                    shortDescription = shortDesc,
                                    createdAt = dateStr,
                                    tags = realTagNames,
                                    imageUrl = imageUrl,
                                    fullDescription = fullDesc,
                                    folderId = folderId
                                )
                                journalEntries.add(journalEntry)
                                journalAdapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load folder's journals", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Converts tag IDs to real tag names.
     */
    private fun fetchTagNames(tagIds: List<String>, callback: (List<String>) -> Unit) {
        if (tagIds.isEmpty()) {
            callback(emptyList())
            return
        }
        firestore.collection("tags")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), tagIds)
            .get()
            .addOnSuccessListener { result ->
                val names = result.documents.map { it.getString("tagName") ?: "Unknown" }
                callback(names)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    private fun getFirst20Words(text: String): String {
        val words = text.split("\\s+".toRegex()).take(20)
        return words.joinToString(" ") + if (words.size == 20) "..." else ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"
        fun newInstance(folderId: String) = EachFolderFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_ID, folderId)
            }
        }
    }
}
