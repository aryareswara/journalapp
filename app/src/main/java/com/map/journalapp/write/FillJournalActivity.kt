package com.map.journalapp.write

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import java.util.Locale
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.ActivityFillJournalBinding

class FillJournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFillJournalBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: String? = null
    private var imageUri: Uri? = null  // Store the URI of the selected image

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference
    private val selectedTagIds = mutableListOf<String>()

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val IMAGE_PICK_CODE = 1001  // Image picker request code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFillJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load tags from Firestore when the activity is created
        loadTagsFromFirestore()

        // Handle the "Allow" option for Journal Written Location
        binding.allowLocationOption.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Request location permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                // Fetch the location if permission is granted
                fetchLocation()
            }
        }

        // Handle image selection
        binding.selectImageButton.setOnClickListener {
            openImagePicker()
        }

        // Button to save journal and redirect to NewNoteActivity
        binding.btnToStory.setOnClickListener {
            saveJournalToFirestoreAndRedirect()
        }

        // Add a new tag when the Add Tag button is clicked
        binding.addTagButton.setOnClickListener {
            val newTag = binding.tagInput.text.toString().trim()
            if (newTag.isNotEmpty()) {
                saveTagToFirestore(newTag)
                binding.tagInput.text.clear()
            } else {
                Toast.makeText(this, "Enter a valid tag", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Request location from the Fused Location Provider
    // Function to fetch and display user location as a readable address
    @SuppressLint("SetTextI18n")
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permissions if not granted
            ActivityCompat.requestPermissions(
                this,
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

                    // Use Geocoder to get address from lat and lon
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (addresses != null) {
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0].getAddressLine(0)
                                binding.locationDisplay.text = address  // Display address
                                userLocation = address  // Store address in userLocation
                            } else {
                                binding.locationDisplay.text = "Address not found"
                                userLocation = "Unknown Location"
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "Error fetching address", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // Open image picker to select an image
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    // Handle the result from the image picker
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data  // Save the URI of the selected image
            binding.journalCoverImage.setImageURI(imageUri)  // Update the ImageView
        }
    }

    // Upload the selected image to Firebase Storage
    private fun uploadImageToFirebase(onSuccess: (String) -> Unit) {
        val storageRef = storage.child("journal_covers/${System.currentTimeMillis()}.jpg")
        imageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        onSuccess(downloadUrl.toString())  // Return the download URL
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Load tags from Firestore that belong to the current user
    private fun loadTagsFromFirestore() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // Ensure the user is authenticated

        firestore.collection("tags")
            .whereEqualTo("userId", userId)  // Fetch tags only for the current user
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    // Dynamically add chips for each tag from the database
                    val chip = Chip(this)
                    chip.text = tagName
                    chip.isCheckable = true

                    // Add or remove tag ID from selectedTagIds when the chip is checked/unchecked
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedTagIds.add(tagId)  // Add the tag ID
                        } else {
                            selectedTagIds.remove(tagId)  // Remove the tag ID
                        }
                    }

                    // Add chip to the ChipGroup
                    binding.tagChipGroup.addView(chip)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load tags", Toast.LENGTH_SHORT).show()
            }
    }

    // Save a new tag to the tags collection in Firestore
    private fun saveTagToFirestore(newTag: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // Ensure the user is authenticated

        val tagData = hashMapOf(
            "tagName" to newTag,
            "userId" to userId  // Bind the tag to the current user
        )

        firestore.collection("tags")
            .add(tagData)
            .addOnSuccessListener { documentReference ->
                val tagId = documentReference.id
                selectedTagIds.add(tagId)  // Add the tag ID to selectedTagIds
                Toast.makeText(this, "Tag added: $newTag", Toast.LENGTH_SHORT).show()

                // Dynamically create a chip for the new tag
                val chip = Chip(this)
                chip.text = newTag
                chip.isCheckable = true
                chip.isChecked = true

                // Add or remove tag ID when the chip is checked/unchecked
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTagIds.add(tagId)
                    } else {
                        selectedTagIds.remove(tagId)
                    }
                }

                // Add the new chip to the ChipGroup
                binding.tagChipGroup.addView(chip)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to add tag", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to save the journal and its tags to Firestore
    private fun saveJournalToFirestoreAndRedirect() {
        val title = binding.journalTitleInput.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        // If an image is selected, upload it to Firebase Storage
        if (imageUri != null) {
            uploadImageToFirebase { imageUrl ->
                // Once the image is uploaded and we have the download URL, save the journal
                saveJournalToFirestore(title, imageUrl)
            }
        } else {
            // If no image is selected, save the journal without an image
            saveJournalToFirestore(title, null)
        }
    }

    private fun saveJournalToFirestore(title: String, imageUrl: String?) {
        // Get the current user's UID
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // Ensure the user is authenticated

        // Prepare the journal data
        val journalData = hashMapOf(
            "title" to title,
            "created_at" to System.currentTimeMillis(),  // Save current timestamp
            "tags" to selectedTagIds,  // Save selected tag IDs
            "location" to userLocation,  // Save the fetched user location
            "image_url" to imageUrl,  // Save the image URL if available
            "userId" to userId  // Bind the journal to the user's UID
        )

        // Save journal to Firestore
        firestore.collection("journals")
            .add(journalData)
            .addOnSuccessListener { documentReference ->
                val journalId = documentReference.id
                navigateToNewNoteActivity(journalId, title)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save journal", Toast.LENGTH_SHORT).show()
            }
    }

    // Navigate to NewNoteActivity after saving the journal
    private fun navigateToNewNoteActivity(journalId: String, title: String) {
        val intent = Intent(this, NewNoteActivity::class.java).apply {
            putExtra("journalId", journalId)
            putExtra("journalTitle", title)
            putStringArrayListExtra("journalTags", ArrayList(selectedTagIds))  // Pass the selected tag IDs
        }
        startActivity(intent)
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)  // Call the super method
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted
                Log.d("LOCATION_FETCH", "Location permission granted")
                fetchLocation() // Fetch location now
            } else {
                Log.w("LOCATION_FETCH", "Location permission denied")
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}