package com.map.journalapp.write

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.MainActivity
import com.map.journalapp.R
import com.map.journalapp.databinding.FragmentFillNoteBinding
import com.map.journalapp.mainActivity.HomeFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FillNoteFragment : Fragment() {

    private var _binding: FragmentFillNoteBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference

    private var journalId: String? = null
    private var journalTitle: String? = null
    private var journalTags: ArrayList<String>? = null

    private val IMAGE_PICK_CODE = 2001
    private val CAMERA_REQUEST_CODE = 2002
    private val CAMERA_PERMISSION_CODE = 3001

    private var chosenImageUri: Uri? = null
    private var photoUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFillNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        journalId = arguments?.getString("journalId")
        journalTitle = arguments?.getString("journalTitle")
        journalTags = arguments?.getStringArrayList("journalTags")

        binding.journalTitleDisplay.text = journalTitle

        val noteContent = arguments?.getString("noteContent")
        if (!noteContent.isNullOrEmpty()) {
            binding.journalContentInput.setText(noteContent)
        }

        val existingImageUrl = arguments?.getString("image_url")
        if (!existingImageUrl.isNullOrEmpty()) {
            chosenImageUri = Uri.parse(existingImageUrl)
            displaySelectedImage(chosenImageUri!!)
        }

        displayTags()

        binding.btnAddImage.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnSave.setOnClickListener {
            saveNoteToFirestore()
        }

        binding.btnDelete.setOnClickListener {
            deleteNoteFromFirestore()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                        }
                    }
                    1 -> pickImageFromGallery()
                }
            }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_$timeStamp.jpg"
        val storageDir = requireContext().cacheDir
        val imageFile = File(storageDir, imageFileName)
        photoUri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".provider", imageFile)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    private fun displaySelectedImage(uri: Uri) {
        // Ensure ImageView in XML has: android:adjustViewBounds="true"
        // and no scaleType or scaleType="fitCenter" for better control
        binding.chosenImageView.adjustViewBounds = true
        Glide.with(this)
            .load(uri)
            .centerInside()
            .override(600,200)
            .into(binding.chosenImageView)
        binding.chosenImageView.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            chosenImageUri = data?.data
            chosenImageUri?.let { displaySelectedImage(it) }
        }

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            chosenImageUri = photoUri
            chosenImageUri?.let { displaySelectedImage(it) }
        }
    }

    private fun displayTags() {
        if (journalTags != null && journalTags!!.isNotEmpty()) {
            for (tagId in journalTags!!) {
                firestore.collection("tags").document(tagId).get()
                    .addOnSuccessListener { document ->
                        val tagName = document.getString("tagName")
                        if (!tagName.isNullOrEmpty()) {
                            val chip = LayoutInflater.from(requireContext())
                                .inflate(R.layout.tagchip_static, binding.tagContainer, false) as Chip
                            chip.text = tagName
                            chip.isClickable = false
                            binding.tagContainer.addView(chip)
                        }
                    }
                    .addOnFailureListener { exception ->
                        exception.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to load tag: $tagId", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun saveNoteToFirestore() {
        val content = binding.journalContentInput.text.toString()
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Please write something in the note", Toast.LENGTH_SHORT).show()
            return
        }
        if (journalId == null) {
            Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
            return
        }

        if (chosenImageUri != null && !chosenImageUri.toString().startsWith("http")) {
            uploadImageToFirebase { imageUrl ->
                saveNoteDocument(content, imageUrl)
                updateJournalImageUrl(imageUrl)
            }
        } else {
            val existingImageUrl = if (chosenImageUri != null && chosenImageUri.toString().startsWith("http")) chosenImageUri.toString() else null
            saveNoteDocument(content, existingImageUrl)
            if (!existingImageUrl.isNullOrEmpty()) {
                updateJournalImageUrl(existingImageUrl)
            }
        }
    }

    private fun updateJournalImageUrl(imageUrl: String) {
        // Update the main journals doc so HomeFragment can display it
        firestore.collection("journals").document(journalId!!)
            .update("image_url", imageUrl)
            .addOnSuccessListener {
                // updated image_url in main doc
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update journal image url", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveNoteDocument(content: String, imageUrl: String?) {
        val noteData = hashMapOf(
            "content" to content,
            "created_at" to System.currentTimeMillis(),
            "image_url" to imageUrl
        )

        firestore.collection("journals").document(journalId!!)
            .collection("notes")
            .add(noteData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment())
                    .addToBackStack(null)
                    .commit()
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                Toast.makeText(requireContext(), "Failed to save note: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImageToFirebase(onSuccess: (String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: "unknownUser"
        val fileName = "notes_images/${userId}_${System.currentTimeMillis()}.jpg"
        val ref = storageRef.child(fileName)

        chosenImageUri?.let { uri ->
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                        onSuccess(downloadUrl.toString())
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteNoteFromFirestore() {
        if (journalId != null) {
            firestore.collection("journals").document(journalId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Journal deleted successfully", Toast.LENGTH_SHORT).show()
//                    requireActivity().supportFragmentManager.popBackStack()

                    val intent = Intent(requireContext(), MainActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to delete journal: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Journal ID is null", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
