// File: SettingFragment.kt
package com.map.journalapp.mainActivity

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentSettingBinding
import java.io.File

/**
 * A Fragment that allows users to view and update their profile information,
 * including their profile picture and username.
 */
class SettingFragment : Fragment() {

    // View Binding
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // URI for profile image
    private var profileImageUri: Uri? = null

    // Flag to track editing state
    private var isEditing = false

    // Listener to notify MainActivity about profile image updates
    private var listener: OnProfileImageUpdatedListener? = null

    /**
     * Interface to communicate profile image updates to the hosting Activity.
     */
    interface OnProfileImageUpdatedListener {
        fun onProfileImageUpdated()
    }

    /**
     * Attaches the listener when the fragment is associated with its context.
     * Ensures that the hosting Activity implements the required interface.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnProfileImageUpdatedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnProfileImageUpdatedListener")
        }
    }

    /**
     * Detaches the listener to prevent memory leaks.
     */
    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * Inflates the fragment's layout using View Binding.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Initializes Firebase instances and sets up UI interactions after the view is created.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Get the current user
        val user = auth.currentUser
        user?.let {
            fetchUserData(it.uid)
        }

        // Set up Edit/Save button logic
        binding.btnEditSave.setOnClickListener {
            if (isEditing) {
                val newName = binding.settingNameEdit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateUserName(user?.uid, newName)
                } else {
                    binding.settingNameEdit.error = "Name cannot be empty"
                    binding.settingNameEdit.requestFocus()
                }
            } else {
                // Switch to edit mode
                binding.settingNameEdit.setText(binding.settingNameTextView.text)
                binding.settingNameEdit.visibility = View.VISIBLE
                binding.settingNameTextView.visibility = View.GONE

                binding.btnEditSave.text = "Save"
                isEditing = true
            }
        }

        // Set up profile picture click listener to initiate image selection
        binding.imgProfilePicture.setOnClickListener {
            showImagePickerDialog()
        }
    }

    /**
     * Fetches the user's data (username and profile picture) from Firestore and updates the UI.
     *
     * @param userId The unique identifier of the user.
     */
    private fun fetchUserData(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userName = document.getString("name") ?: "Username"
                    val profilePicture = document.getString("profilePicture")

                    // Update the username in the UI
                    binding.settingNameTextView.text = userName

                    // Load the profile picture using Glide
                    Glide.with(this)
                        .load(profilePicture)
                        .apply(
                            RequestOptions()
                                .circleCrop()
                                .placeholder(R.drawable.person) // Default placeholder
                                .error(R.drawable.person) // Error placeholder
                        )
                        .into(binding.imgProfilePicture)
                }
            }
            .addOnFailureListener {
                binding.settingNameTextView.text = "Error fetching user data"
                binding.imgProfilePicture.setImageResource(R.drawable.person)
                Toast.makeText(requireContext(), "Error fetching user data", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Updates the user's username in Firestore and refreshes the UI.
     *
     * @param userId The unique identifier of the user.
     * @param newName The new username to be updated.
     */
    private fun updateUserName(userId: String?, newName: String) {
        userId?.let {
            val updates = mapOf("name" to newName)

            firestore.collection("users").document(userId).update(updates)
                .addOnSuccessListener {
                    // Update the UI with the new username
                    binding.settingNameTextView.text = newName
                    binding.settingNameEdit.visibility = View.GONE
                    binding.settingNameTextView.visibility = View.VISIBLE
                    binding.btnEditSave.text = "Edit"
                    isEditing = false
                    Toast.makeText(requireContext(), "Username updated successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to update name: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * Displays a dialog allowing the user to choose between Camera and Gallery for image selection.
     */
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

    /**
     * Registers a callback for selecting an image from the gallery.
     */
    private val getImageFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            profileImageUri = it
            binding.imgProfilePicture.setImageURI(it)
            uploadProfileImage(it)
        }
    }

    /**
     * Launches the gallery picker to allow the user to select an image.
     */
    private fun openGallery() {
        getImageFromGallery.launch("image/*")
    }

    /**
     * Registers a callback for taking a picture using the camera.
     */
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            profileImageUri?.let {
                binding.imgProfilePicture.setImageURI(it)
                uploadProfileImage(it)
            }
        } else {
            Toast.makeText(requireContext(), "Failed to take picture", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches the camera to capture a new profile picture.
     */
    private fun openCamera() {
        val photoFile = File(requireContext().cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
        profileImageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        takePicture.launch(profileImageUri)
    }

    /**
     * Uploads the selected or captured profile image to Firebase Storage.
     *
     * @param uri The URI of the image to be uploaded.
     */
    private fun uploadProfileImage(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                // Retrieve the download URL after a successful upload
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveProfileImageUrlToFirestore(userId, downloadUrl.toString())
                    // Optionally, update the ImageView immediately with the new image
                    Glide.with(this)
                        .load(downloadUrl.toString())
                        .apply(
                            RequestOptions()
                                .circleCrop()
                                .placeholder(R.drawable.person)
                                .error(R.drawable.person)
                        )
                        .into(binding.imgProfilePicture)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Saves the download URL of the uploaded profile image to Firestore.
     *
     * @param userId The unique identifier of the user.
     * @param imageUrl The download URL of the uploaded profile image.
     */
    private fun saveProfileImageUrlToFirestore(userId: String, imageUrl: String) {
        firestore.collection("users").document(userId)
            .update("profilePicture", imageUrl)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile image updated successfully!", Toast.LENGTH_SHORT).show()
                listener?.onProfileImageUpdated() // Notify the Activity to refresh data
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update profile image URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Cleans up the binding when the view is destroyed to prevent memory leaks.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
