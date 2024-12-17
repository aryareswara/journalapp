package com.map.journalapp.write

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentFillNoteBinding
import com.map.journalapp.databinding.FragmentViewNoteBinding

class ViewNoteFragment : Fragment() {
    private var _binding: FragmentViewNoteBinding? = null
    private val binding get() = _binding!!

    private var journalId: String? = null
    private var journalTitle: String? = null
    private var fullDescription: String? = null
    private var imageUrl: String? = null
    private var tags: ArrayList<String>? = null
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        fullDescription = arguments?.getString("fullDescription")
        imageUrl = arguments?.getString("image_url")
        tags = arguments?.getStringArrayList("tags")

        binding.journalTitleDisplay.text = journalTitle
        binding.journalContentDisplay.text = fullDescription

        if (!imageUrl.isNullOrEmpty()) {
            binding.chosenImageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .override(600, 200)
                .into(binding.chosenImageView)
        }

        displayTags()

        binding.btnEdit.setOnClickListener {
            // Navigate to EditNoteFragment (similar to FillNoteFragment but for editing)
            val editFragment = EditNoteFragment().apply {
                arguments = Bundle().apply {
                    putString("journalId", journalId)
                    putString("journalTitle", journalTitle)
                    putString("fullDescription", fullDescription)
                    putString("image_url", imageUrl)
                    putStringArrayList("tags", tags)
                }
            }
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, editFragment)
                .addToBackStack(null)
                .commit()
        }

    }

    private fun displayTags() {
        if (tags != null && tags!!.isNotEmpty()) {
            for (tagName in tags!!) {
                val chip = Chip(requireContext())
                chip.text = tagName
                chip.isClickable = false
                binding.tagContainer.addView(chip)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
