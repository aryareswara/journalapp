package com.map.journalapp.mainActivity

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
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentSettingBinding
import com.map.journalapp.logreg.LoginActivity

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
            fetchUserName(it.uid)
            fetchUserProfileImage(it.uid)
        }

        // Set up upload photo button
        binding.btnUploadPhoto.setOnClickListener {
            showImagePickerDialog()
        }

        // Set up edit/save button
        binding.btnEditSave.setOnClickListener {
            if (isEditing) {
                val newName = binding.settingNameEdit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateUserName(user?.uid, newName)
                }
            } else {
                binding.settingNameEdit.setText(binding.settingNameTextView.text)
                binding.settingNameEdit.visibility = View.VISIBLE
                binding.settingNameTextView.visibility = View.GONE
                binding.btnEditSave.text = "Save"
                isEditing = true
            }
        }

        // Set up logout button
        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

        return binding.root
    }

    private fun fetchUserName(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userName = document.getString("name")
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
                        Glide.with(this)
                            .load(profileImageUrl)
                            .into(binding.imgProfilePicture)
                    } else {
                        binding.imgProfilePicture.setImageResource(R.drawable.person)
                    }
                } else {
                    binding.imgProfilePicture.setImageResource(R.drawable.person)
                }
            }
            .addOnFailureListener {
                binding.imgProfilePicture.setImageResource(R.drawable.person)
            }
    }

    private fun updateUserName(userId: String?, newName: String) {
        if (userId != null) {
            val updates = mapOf("name" to newName)

            firestore.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    binding.settingNameTextView.text = newName
                    binding.settingNameEdit.visibility = View.GONE
                    binding.settingNameTextView.visibility = View.VISIBLE
                    binding.btnEditSave.text = "Edit"
                    isEditing = false
                }
                .addOnFailureListener {
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
        intent.type = "image/*"
        startForResult.launch(intent)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startForResult.launch(intent)
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                profileImageUri = data?.data
                binding.imgProfilePicture.setImageURI(profileImageUri)

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
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveProfileImageUrlToFirestore(userId, downloadUrl.toString())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfileImageUrlToFirestore(userId: String, imageUrl: String) {
        firestore.collection("users").document(userId)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update profile image URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logoutUser() {
        auth.signOut()
        // Navigate to the login screen or handle post-logout actions
        val intent = Intent(requireContext(), LoginActivity::class.java) // Replace with your login activity
        startActivity(intent)
        activity?.finish() // Close the current activity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

