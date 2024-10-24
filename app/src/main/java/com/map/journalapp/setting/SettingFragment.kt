package com.map.journalapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.databinding.FragmentSettingBinding

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private var profileImageUri: Uri? = null
    private var isEditing = false

    // Firebase Auth, Firestore, and Storage instances
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)

        // Initialize Firebase Auth, Firestore, and Storage
        auth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore
        storage = FirebaseStorage.getInstance()

        // Get current user
        val user = auth.currentUser

        // Fetch user name and profile image from Firestore
        user?.let {
            fetchUserName(it.uid) // Fetch name using user ID
            fetchUserProfileImage(it.uid) // Fetch profile image
        }

        // Set up upload photo button
        binding.btnUploadPhoto.setOnClickListener {
            showImagePickerDialog()
        }

        // Set up edit/save button
        binding.btnEditSave.setOnClickListener {
            if (isEditing) {
                // Save changes to the name
                val newName = binding.settingNameEdit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateUserName(user?.uid, newName) // Update name in Firestore
                }
            } else {
                // Enter edit mode
                binding.settingNameEdit.setText(binding.settingNameTextView.text) // Fill EditText with current name
                binding.settingNameEdit.visibility = View.VISIBLE // Show EditText
                binding.settingNameTextView.visibility = View.GONE // Hide TextView
                binding.btnEditSave.text = "Save" // Change button to "Save"
                isEditing = true
            }
        }

        return binding.root
    }

    private fun fetchUserName(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userName = document.getString("name") // Adjust with the field name in Firestore
                    binding.settingNameTextView.text = userName ?: "No Name"
                } else {
                    binding.settingNameTextView.text = "No Name"
                }
            }
            .addOnFailureListener { exception ->
                binding.settingNameTextView.text = "Error fetching name"
            }
    }

    private fun fetchUserProfileImage(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val profileImageUrl = document.getString("profileImageUrl")
                    if (!profileImageUrl.isNullOrEmpty()) {
                        // Use Glide to load the profile image from the URL
                        Glide.with(this)
                            .load(profileImageUrl)
                            .into(binding.imgProfilePicture)
                    } else {
                        // Load default image from drawable
                        binding.imgProfilePicture.setImageResource(R.drawable.person) // Ganti dengan nama drawable Anda
                    }
                } else {
                    // Load default image from drawable
                    binding.imgProfilePicture.setImageResource(R.drawable.person) // Ganti dengan nama drawable Anda
                }
            }
            .addOnFailureListener { exception ->
                // Load default image from drawable in case of an error
                binding.imgProfilePicture.setImageResource(R.drawable.person) // Ganti dengan nama drawable Anda
            }
    }

    private fun updateUserName(userId: String?, newName: String) {
        if (userId != null) {
            val updates = mutableMapOf<String, Any>(
                "name" to newName
            )

            firestore.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    binding.settingNameTextView.text = newName // Update displayed name
                    binding.settingNameEdit.visibility = View.GONE // Hide EditText
                    binding.settingNameTextView.visibility = View.VISIBLE // Show TextView
                    binding.btnEditSave.text = "Edit" // Change button back to "Edit"
                    isEditing = false
                }
                .addOnFailureListener { e ->
                    // Handle the error
                }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Camera", "Gallery")
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select Image Source")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*" // Use ACTION_GET_CONTENT to get images
        startForResult.launch(intent) // Launch the intent
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startForResult.launch(intent) // Launch the camera intent
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                profileImageUri = data?.data
                binding.imgProfilePicture.setImageURI(profileImageUri)

                // After getting the image URI, upload it to Firebase Storage
                profileImageUri?.let { uri ->
                    uploadProfileImage(uri)
                }
            }
        }

    private fun uploadProfileImage(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                // Get the download URL of the uploaded image
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    // Save the URL to Firestore
                    saveProfileImageUrlToFirestore(userId, downloadUrl.toString())
                }
            }
            .addOnFailureListener { e ->
                // Handle upload error
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfileImageUrlToFirestore(userId: String, imageUrl: String) {
        firestore.collection("users").document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                // URL successfully saved
                Toast.makeText(requireContext(), "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Handle error when saving URL
                Toast.makeText(requireContext(), "Failed to update profile image URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
