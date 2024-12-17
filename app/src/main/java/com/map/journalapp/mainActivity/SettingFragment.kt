package com.map.journalapp.mainActivity

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentSettingBinding
import com.map.journalapp.logreg.LoginActivity
import java.io.File
import com.bumptech.glide.request.RequestOptions

class SettingFragment : Fragment() {
    // using binding
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private var profileImageUri: Uri? = null

    // bool to know change the name or not
    private var isEditing = false

    // connect firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // to know captured image uri
    private lateinit var photoUri: Uri

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)

        // initialize firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val user = auth.currentUser

        // fetch user data
        user?.let {
            fetchUserData(it.uid)
            // fetchUserName(it.uid)
            // fetchUserProfileImage(it.uid)
        }

        // Edit/Save button logic
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


        // open image picker on profile picture click
        binding.imgProfilePicture.setOnClickListener {
            showImagePickerDialog()
        }

        return binding.root
    }

    // func for fetching user's name and pfp
    private fun fetchUserData(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userName = document.getString("name") ?: "Username"
                    val profilePicture = document.getString("profilePicture")

                    // Change name into user's name
                    binding.settingNameTextView.text = userName

                    // Load the image with Glide, centerCrop and rounded corners
                    Glide.with(this)
                        .load(profilePicture)
                        .apply(
                            RequestOptions()
                                .circleCrop()
                                .placeholder(R.drawable.person)
                                .error(R.drawable.person)
                        )
                        .into(binding.imgProfilePicture)
                }
            }
            .addOnFailureListener {
                binding.settingNameTextView.text = "Error fetching user data"
                binding.imgProfilePicture.setImageResource(R.drawable.person)
            }
    }

    // func for update username
    private fun updateUserName(userId: String?, newName: String) {
        userId?.let {
            val updates = mapOf("name" to newName)

            firestore.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    binding.settingNameTextView.text = newName
                    binding.settingNameEdit.visibility = View.GONE
                    binding.settingNameTextView.visibility = View.VISIBLE
                    binding.btnEditSave.text = "Edit"
                    isEditing = false
                }
        }
    }

    // func for give option to upload image
    private fun showImagePickerDialog() {
        val options = arrayOf("Camera", "Gallery")
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select Image Source")
        builder.setItems(options) { _, which ->
            when (which) {
                // from camera
                0 -> openCamera()
                // from gallery
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            profileImageUri = it
            binding.imgProfilePicture.setImageURI(it)
            uploadProfileImage(it)
        }
    }

    private fun openGallery() {
        getImageFromGallery.launch("image/*")
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            profileImageUri?.let {
                binding.imgProfilePicture.setImageURI(it)
                uploadProfileImage(it)
            }
        }
    }

    private fun openCamera() {
        val photoFile = File(requireContext().cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
        profileImageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        takePicture.launch(profileImageUri)
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
            .update("profilePicture", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update profile image URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
