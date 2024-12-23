package com.map.journalapp.write

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.adapter_model.Folder
import com.map.journalapp.databinding.FragmentJournalDetailBinding
import java.util.Locale

class JournalDetailFragment : Fragment() {

    private var _binding: FragmentJournalDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: String? = null
    private var imageUri: Uri? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference
    private val selectedTagIds = mutableListOf<String>()

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val IMAGE_PICK_CODE = 1001

    // Folder picking
    private lateinit var folderSpinner: Spinner
    private val folderList = mutableListOf<Folder>()
    private val folderNames = mutableListOf<String>()
    private var selectedFolderId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJournalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        binding.allowLocationOption.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                fetchLocation()
            }
        }

        loadTagsFromFirestore()
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim()
            if (newTag.isNotEmpty()) {
                saveTagToFirestore(newTag)
                binding.tagInput.text.clear()
            }
        }

        // Folder spinner
        folderSpinner = binding.spinnerFolders
        loadUserFolders { folders ->
            folderList.clear()
            folderList.addAll(folders)

            folderNames.clear()
            folderNames.addAll(folders.map { it.fileName })

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, folderNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            folderSpinner.adapter = adapter

            folderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedFolderId = null
                }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedFolderId = folderList[position].id
                }
            }
        }

        binding.locationOptionGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.allowLocationOption) {
                binding.locationDisplay.visibility = View.VISIBLE
            } else {
                userLocation = null
                binding.locationDisplay.visibility = View.GONE
            }
        }

        binding.btnToStory.setOnClickListener {
            saveJournalToFirestoreAndRedirect()
        }
    }

    // LOCATION
    @SuppressLint("SetTextI18n")
    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        binding.locationDisplay.text = address
                        userLocation = address
                    }
                } else {
                    Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // FOLDERS
    private fun loadUserFolders(callback: (List<Folder>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("folders")
            .whereEqualTo("user_id", userId)  // match your doc fields
            .get()
            .addOnSuccessListener { snap ->
                val result = snap.documents.mapNotNull { doc ->
                    doc.toObject(Folder::class.java)?.copy(id = doc.id)
                }
                callback(result)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    // TAGS
    private fun loadTagsFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("tags")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    val chip = Chip(requireContext())
                    chip.text = tagName
                    chip.isCheckable = true
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedTagIds.add(tagId) else selectedTagIds.remove(tagId)
                    }
                    binding.tagChipGroup.addView(chip)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveTagToFirestore(newTag: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        val tagData = mapOf("tagName" to newTag, "userId" to userId)
        firestore.collection("tags")
            .add(tagData)
            .addOnSuccessListener { docRef ->
                val tagId = docRef.id
                selectedTagIds.add(tagId)

                val chip = Chip(requireContext())
                chip.text = newTag
                chip.isCheckable = true
                chip.isChecked = true
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTagIds.add(tagId) else selectedTagIds.remove(tagId)
                }
                binding.tagChipGroup.addView(chip)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add tag", Toast.LENGTH_SHORT).show()
            }
    }

    // CREATE JOURNAL
    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = hashMapOf(
            "title" to title,
            "created_at" to System.currentTimeMillis(),
            "tags" to selectedTagIds,
            "location" to userLocation,
            "userId" to userId
        )
        selectedFolderId?.let { data["folder_id"] = it }

        firestore.collection("journals")
            .add(data)
            .addOnSuccessListener { docRef ->
                Toast.makeText(requireContext(), "Journal created!", Toast.LENGTH_SHORT).show()
                // e.g. navigate to FillNoteFragment or back to Home
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // optional image picking
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // handle images if needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // PERMISSIONS
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
