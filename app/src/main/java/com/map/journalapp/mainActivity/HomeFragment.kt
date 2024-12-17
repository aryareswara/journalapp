package com.map.journalapp.mainActivity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalEntry

class HomeFragment : Fragment() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): ComposeView {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    HomeScreen(
                        journalEntries = homeViewModel.journalEntries,
                        onAddJournalClick = { openFillJournalFragment() },
                        onJournalClick = { journalEntry -> openNoteFragment(journalEntry) }
                    )
                }
            }
        }
    }

    private fun openNoteFragment(journalEntry: JournalEntry) {
        val bundle = Bundle().apply {
            putString("journalId", journalEntry.id)
            putString("journalTitle", journalEntry.title)
            putString("noteContent", journalEntry.fullDescription)
        }
        findNavController().navigate(R.id.newNoteFragment, bundle)
    }

    private fun openFillJournalFragment() {
        findNavController().navigate(R.id.fillJournalFragment)
    }
}

class HomeViewModel : androidx.lifecycle.ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var journalEntries by mutableStateOf(listOf<JournalEntry>())
        private set

    init {
        loadJournals()
    }

    private fun loadJournals() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("journals")
            .whereEqualTo("userId", userId)
            .orderBy("created_at")
            .get()
            .addOnSuccessListener { result ->
                journalEntries = result.map { document ->
                    JournalEntry(
                        id = document.id,
                        title = document.getString("title") ?: "No Title",
                        shortDescription = "Preview unavailable",
                        createdAt = document.getLong("created_at").toString(),
                        tags = document.get("tags") as? List<String> ?: listOf(),
                        imageUrl = document.getString("image_url"),
                        fullDescription = document.getString("content") ?: "No content"
                    )
                }
            }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun HomeScreen(
    journalEntries: List<JournalEntry>,
    onAddJournalClick: () -> Unit,
    onJournalClick: (JournalEntry) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddJournalClick) {
                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add Journal")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(journalEntries) { journal ->
                JournalCard(journalEntry = journal, onClick = { onJournalClick(journal) })
            }
        }
    }
}

@Composable
fun JournalCard(
    journalEntry: JournalEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = journalEntry.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = journalEntry.shortDescription,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                journalEntry.tags.forEach { tag ->
                    Chip(text = tag)
                }
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(Color.Gray, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    val sampleJournalEntries = listOf(
        JournalEntry(
            id = "1",
            title = "Sample Journal 1",
            shortDescription = "This is a short description of sample journal 1.",
            createdAt = "01.01.2023 12:00",
            tags = listOf("Tag1", "Tag2"),
            imageUrl = null,
            fullDescription = "This is the full description of sample journal 1.",
        ),
        JournalEntry(
            id = "2",
            title = "Sample Journal 2",
            shortDescription = "This is a short description of sample journal 2.",
            createdAt = "02.01.2023 12:00",
            tags = listOf("Tag3", "Tag4"),
            imageUrl = null,
            fullDescription = "This is the full description of sample journal 2.",
        )
    )

    MaterialTheme {
        HomeScreen(
            journalEntries = sampleJournalEntries,
            onAddJournalClick = {},
            onJournalClick = {}
        )
    }
}
