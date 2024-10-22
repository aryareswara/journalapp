package com.map.journalapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.map.journalapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var journalAdapter: JournalAdapter

    // Sample data for the RecyclerView
    private val sampleJournals = listOf(
        JournalEntry("Ini Journal 1", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "01.01.2024"),
        JournalEntry("Ini Journal 2", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "02.01.2024"),
        JournalEntry("Ini Journal 3", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", "03.01.2024")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment using ViewBinding
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Set up RecyclerView
        binding.journalRecycle.layoutManager = LinearLayoutManager(requireContext())
        journalAdapter = JournalAdapter(sampleJournals)
        binding.journalRecycle.adapter = journalAdapter

        return binding.root
    }
}
