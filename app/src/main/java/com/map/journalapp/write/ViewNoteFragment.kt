package com.map.journalapp.write

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentViewNoteBinding

class ViewNoteFragment : Fragment() {
    private var _binding: FragmentViewNoteBinding? = null
    private val binding get() = _binding!!

    private var journalId: String? = null
    private var journalTitle: String? = null
    private var imageUrl: String? = null
    private var tags: ArrayList<String>? = null

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        imageUrl = arguments?.getString("image_url")
        tags = arguments?.getStringArrayList("tags")

        binding.journalTitleDisplay.text = journalTitle

        fetchLatestNote()

        imageUrl?.let {
            if (it.isNotEmpty()) {
                displaySelectedImage(it)
            }
        }

        displayTags()

        binding.btnEdit.setOnClickListener {
            // Navigate to FillNoteFragment for editing
            val fillNoteFragment = FillNoteFragment().apply {
                arguments = Bundle().apply {
                    putString("journalId", journalId)
                    putString("journalTitle", journalTitle)
                    putString("noteContent", binding.journalContentDisplay.text.toString())
                    putString("image_url", imageUrl)
                    putStringArrayList("journalTags", tags)
                }
            }
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fillNoteFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun fetchLatestNote() {
        if (journalId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Invalid journal ID", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("journals")
            .document(journalId!!)
            .collection("notes")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val latestNote = result.documents.firstOrNull()
                val fullDescription = latestNote?.getString("content") ?: "No Notes Available"
                binding.journalContentDisplay.text = fullDescription
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to fetch note: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displaySelectedImage(image: Any) {
        binding.chosenImageView.adjustViewBounds = true
        Glide.with(this)
            .load(image)
            .centerInside()
            .override(600, 200)
            .into(binding.chosenImageView)
        binding.chosenImageView.visibility = View.VISIBLE
    }

    private fun displayTags() {
        if (tags != null && tags!!.isNotEmpty()) {
            binding.tagContainer.removeAllViews() // Clear existing tags to avoid duplication
            for (tagName in tags!!) {
                val chip = LayoutInflater.from(requireContext())
                    .inflate(R.layout.tagchip_static, binding.tagContainer, false) as Chip
                chip.text = tagName
                binding.tagContainer.addView(chip)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
