package com.map.journalapp.mainActivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.R
import com.map.journalapp.adapter_model.JournalAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.write.FillJournalFragment

class HomeFragment : Fragment() {

    private lateinit var journalAdapter: JournalAdapter
    private val journalEntries = mutableListOf<JournalEntry>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize the RecyclerView
        val recyclerView: RecyclerView = view.findViewById(R.id.journalRecycle)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data for the RecyclerView
        journalEntries.addAll(listOf(
            JournalEntry("Ini Journal 1", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "01.01.2024"),
            JournalEntry("Ini Journal 2", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "02.01.2024"),
            JournalEntry("Ini Journal 3", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "03.01.2024"),
            JournalEntry("Ini Journal 4", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "04.01.2024"),
            JournalEntry("Ini Journal 5", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "05.01.2024")
        ))

        // Set the adapter with the initial data
        journalAdapter = JournalAdapter(journalEntries)
        recyclerView.adapter = journalAdapter

        // New Journal Button click listener to navigate to FillJournalFragment
        val newJournalButton: LinearLayout = view.findViewById(R.id.newJournalButton)
        newJournalButton.setOnClickListener {
            // Use FragmentTransaction to replace the current fragment with FillJournalFragment
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, FillJournalFragment()) // fragment_container is the ID of your FrameLayout where fragments are displayed
            transaction.addToBackStack(null)  // Add the transaction to the back stack so the user can navigate back
            transaction.commit()
        }

        return view
    }
}
