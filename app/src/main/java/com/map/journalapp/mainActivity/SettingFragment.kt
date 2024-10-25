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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentSettingBinding
import com.map.journalapp.logreg.LoginActivity
import java.io.File

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private var profileImageUri: Uri? = null
    private var isEditing = false

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var photoUri: Uri

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val user = auth.currentUser

        user?.let {
            fetchUserName(it.uid)
            fetchUserProfileImage(it.uid)
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

        // Logout button
        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

        // Open image picker on profile picture click
        binding.imgProfilePicture.setOnClickListener {
            showImagePickerDialog()
        }


        return binding.root
    }

    private fun fetchUserName(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val userName = document?.getString("name") ?: "No Name"
                binding.settingNameTextView.text = userName
            }
            .addOnFailureListener {
                binding.settingNameTextView.text = "Error fetching name"
            }
    }

    private fun fetchUserProfileImage(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val profileImageUrl = document?.getString("profileImageUrl")
                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(profileImageUrl).into(binding.imgProfilePicture)
                } else {
                    binding.imgProfilePicture.setImageResource(R.drawable.person)
                }
            }
            .addOnFailureListener {
                binding.imgProfilePicture.setImageResource(R.drawable.person)
            }
    }

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
                .addOnFailureListener {
                    // Handle error
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
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(android.Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            val photoFile = File(requireContext().cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startForResult.launch(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                profileImageUri = result.data?.data ?: photoUri

                // Set image to ImageView and show the Save and Use button
                binding.imgProfilePicture.setImageURI(profileImageUri)
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
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val CAMERA_REQUEST_CODE = 100
    }
}
