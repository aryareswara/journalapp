package com.map.journalapp.write

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.adapter_model.Folder
import com.map.journalapp.adapter_model.FolderAdapter
import com.map.journalapp.databinding.FragmentJournalDetailBinding
import java.util.Locale

class JournalDetailFragment : Fragment() {

    private var _binding: FragmentJournalDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: String? = null
    private var imageUri: Uri? = null  // If you want image picking

    private val firestore = FirebaseFirestore.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference
    private val selectedTagIds = mutableListOf<String>()

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val IMAGE_PICK_CODE = 1001

    // Folder selection
    private var folderList: MutableList<Folder> = mutableListOf()
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

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Handle RadioGroup selection changes
        binding.locationOptionGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.allowLocationOption -> {
                    Log.d("LOCATION_OPTION", "Allow Location selected")
                    binding.locationDisplay.visibility = View.VISIBLE
                    checkLocationPermissionAndFetch()
                }
                R.id.notAllowLocationOption -> {
                    Log.d("LOCATION_OPTION", "Don't Allow Location selected")
                    userLocation = null
                    binding.locationDisplay.visibility = View.GONE
                    binding.locationDisplay.text = "Location will appear here if allowed"
                }
            }
        }

        // Initialize Folder Selection Button
        binding.selectFolderButton.setOnClickListener {
            if (folderList.isEmpty()) {
                Toast.makeText(requireContext(), "No folders available to select.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showFolderSelectionDialog()
        }

        // Initialize Tag System
        loadTagsFromFirestore()
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim()
            if (newTag.isNotEmpty()) {
                saveTagToFirestore(newTag)
                binding.tagInput.text.clear()
            } else {
                Toast.makeText(requireContext(), "Enter a valid tag", Toast.LENGTH_SHORT).show()
            }
        }

        // Button to proceed (save journal)
        binding.btnToStory.setOnClickListener {
            saveJournalToFirestoreAndRedirect()
        }

        // Load folders
        loadUserFolders { folders ->
            if (folders.isNotEmpty()) {
                folderList.clear()
                folderList.addAll(folders)
                Log.d("FOLDER_LOADING", "Folders loaded: ${folderList.size}")
            } else {
                Log.w("FOLDER_LOADING", "No folders found for user.")
                // Optionally, disable the selectFolderButton if no folders
                binding.selectFolderButton.isEnabled = false
                binding.selectFolderButton.alpha = 0.5f // Indicate disabled state
                Toast.makeText(requireContext(), "No folders available. Please create one.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Checks for location permission and fetches location if granted.
     */
    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("LOCATION_PERMISSION", "Requesting location permission")
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d("LOCATION_PERMISSION", "Location permission already granted. Fetching location.")
            fetchLocation()
        }
    }

    // LOCATION
    @SuppressLint("SetTextI18n")
    private fun fetchLocation() {
        Log.d("LOCATION_FETCH", "Fetching location...")
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LOCATION_FETCH", "Location permission not granted.")
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
                    Log.d("LOCATION_FETCH", "Location fetched: ($lat, $lon)")
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            Log.d("LOCATION_FETCH", "Address found: $address")
                            binding.locationDisplay.text = address
                            userLocation = address
                        } else {
                            Log.e("LOCATION_FETCH", "No addresses found.")
                            binding.locationDisplay.text = "Address not found"
                            userLocation = "Unknown Location"
                        }
                    } catch (e: Exception) {
                        Log.e("LOCATION_FETCH", "Geocoder exception: ${e.message}")
                        binding.locationDisplay.text = "Error fetching address"
                        userLocation = "Error"
                        Toast.makeText(requireContext(), "Error fetching address", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w("LOCATION_FETCH", "Location is null.")
                    binding.locationDisplay.text = "Location not available"
                    userLocation = "Location not available"
                    Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LOCATION_FETCH", "Failed to get location: ${exception.message}")
                binding.locationDisplay.text = "Failed to get location"
                userLocation = "Failed to get location"
                Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // FOLDERS
    private fun loadUserFolders(callback: (List<Folder>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("FOLDER_LOADING", "User ID is null or empty.")
            callback(emptyList())
            return
        }

        firestore.collection("folders")
            .whereEqualTo("user_id", userId)
            .get()
            .addOnSuccessListener { snap ->
                val folderResult = snap.documents.mapNotNull { doc ->
                    val folderId = doc.id
                    val fileName = doc.getString("file_name")?.takeIf { it.isNotBlank() } ?: "Untitled Folder"

                    // Construct folder model manually
                    Folder(
                        id = folderId,
                        fileName = fileName,
                    )
                }
                Log.d("FOLDER_LOADING", "Folders loaded: ${folderResult.size}")
                folderResult.forEach { folder ->
                    Log.d("FOLDER_LOADING", "Folder: '${folder.fileName}'")
                }
                callback(folderResult)
            }
            .addOnFailureListener { exception ->
                Log.e("FOLDER_LOADING", "Failed to load folders: ${exception.message}")
                callback(emptyList())
            }
    }

    // TAGS
    private fun loadTagsFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("TAGS_LOADING", "User ID is null or empty.")
            return
        }

        firestore.collection("tags")
            .whereEqualTo("userId", userId)  // Ensure this matches your Firestore field
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.d("TAGS_LOADING", "No tags found for user.")
                    return@addOnSuccessListener
                }

                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    val chip = LayoutInflater.from(requireContext()).inflate(R.layout.tagchip, binding.tagChipGroup, false) as Chip
                    chip.text = tagName
                    chip.isChecked = false // Initially unchecked
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedTagIds.add(tagId)
                            Log.d("TAG_SELECTION", "Tag added: $tagId")
                        } else {
                            selectedTagIds.remove(tagId)
                            Log.d("TAG_SELECTION", "Tag removed: $tagId")
                        }
                    }
                    binding.tagChipGroup.addView(chip)
                }
                Log.d("TAGS_LOADING", "Tags loaded: ${selectedTagIds.size}")
            }
            .addOnFailureListener { exception ->
                Log.e("TAGS_LOADING", "Failed to load tags: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveTagToFirestore(newTag: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("TAG_SAVING", "User ID is null or empty.")
            Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        val tagData = mapOf(
            "tagName" to newTag,
            "userId" to userId
        )

        firestore.collection("tags")
            .add(tagData)
            .addOnSuccessListener { docRef ->
                val tagId = docRef.id
                selectedTagIds.add(tagId)
                Log.d("TAG_SAVING", "Tag saved with ID: $tagId")

                val chip = LayoutInflater.from(requireContext()).inflate(R.layout.tagchip, binding.tagChipGroup, false) as Chip
                chip.text = newTag
                chip.isChecked = true // Automatically select the new tag
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTagIds.add(tagId)
                        Log.d("TAG_SELECTION", "Tag added: $tagId")
                    } else {
                        selectedTagIds.remove(tagId)
                        Log.d("TAG_SELECTION", "Tag removed: $tagId")
                    }
                }
                binding.tagChipGroup.addView(chip)
            }
            .addOnFailureListener { exception ->
                Log.e("TAG_SAVING", "Failed to save tag: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to add tag", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showFolderSelectionDialog() {
        // Inflate the custom dialog view
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_folder_selection, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize AlertDialog.Builder
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialog)
            .setView(dialogView)
            .setNegativeButton("Cancel", null) // Set null for customization later

        // Create the AlertDialog
        val dialog = builder.create()

        // Initialize the adapter with access to 'dialog'
        val adapter = FolderAdapter(folderList) { selectedFolder ->
            selectedFolderId = selectedFolder.id
            binding.selectedFolderTextView.text = selectedFolder.fileName
            Log.d("FOLDER_SELECTION", "Selected folder: '${selectedFolder.fileName}' (ID: ${selectedFolder.id})")
            dialog.dismiss() // Now 'dialog' is accessible here
        }
        recyclerView.adapter = adapter

        // Customize buttons after dialog is shown
        dialog.setOnShowListener {
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            negativeButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_font))
        }

        // Show the dialog
        dialog.show()
    }



    // CREATE JOURNAL
    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("JOURNAL_SAVING", "User ID is null or empty.")
            Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
            return
        }

        val journalData = hashMapOf(
            "title" to title,
            "created_at" to System.currentTimeMillis(),
            "tags" to selectedTagIds,
            "location" to userLocation,
            "userId" to userId
        )

        // If a folder is selected, add its ID
        selectedFolderId?.let { journalData["folder_id"] = it }

        firestore.collection("journals")
            .add(journalData)
            .addOnSuccessListener { docRef ->
                Log.d("JOURNAL_SAVING", "Journal created with ID: ${docRef.id}")
                Toast.makeText(requireContext(), "Journal created!", Toast.LENGTH_SHORT).show()

                // Redirect to FillNoteFragment, passing necessary arguments
                redirectToFillNoteFragment(journalId = docRef.id, journalTitle = title)
            }
            .addOnFailureListener { exception ->
                Log.e("JOURNAL_SAVING", "Failed to create journal: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to create journal", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Redirects to FillNoteFragment with the provided journalId and journalTitle.
     */
    private fun redirectToFillNoteFragment(journalId: String, journalTitle: String) {
        // Create an instance of FillNoteFragment
        val fillNoteFragment = FillNoteFragment()

        // Create a bundle to pass arguments
        val bundle = Bundle().apply {
            putString("journalId", journalId)
            putString("journalTitle", journalTitle)
            putStringArrayList("journalTags", ArrayList(selectedTagIds))
            // Add other arguments if necessary
        }

        // Set the arguments to the fragment
        fillNoteFragment.arguments = bundle

        // Perform the fragment transaction
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fillNoteFragment) // Ensure 'fragment_container' is the ID of your container
            .addToBackStack(null) // Add to back stack to allow user to navigate back
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    /**
     * Resets the form after successful journal creation.
     */
    private fun resetForm() {
        binding.journalTitleInput.text.clear()
        binding.selectedFolderTextView.text = "No folder selected"
        selectedFolderId = null
        userLocation = null
        binding.locationDisplay.text = "Location will appear here if allowed"
        binding.tagChipGroup.removeAllViews()
        selectedTagIds.clear()

        // Reload tags from Firestore
        loadTagsFromFirestore()
    }

    // OPTIONAL IMAGE PICKING
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            // Optionally display the selected image in an ImageView
            // binding.selectedImageView.setImageURI(imageUri)
            Log.d("IMAGE_PICKING", "Image selected: $imageUri")
        }
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("PERMISSIONS", "Location permission granted.")
                fetchLocation()
            } else {
                Log.w("PERMISSIONS", "Location permission denied.")
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
                binding.locationDisplay.visibility = View.GONE
                binding.locationDisplay.text = "Location will appear here if allowed"
            }
        }
    }
}
