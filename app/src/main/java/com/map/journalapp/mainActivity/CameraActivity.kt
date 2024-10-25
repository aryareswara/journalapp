package com.map.journalapp.mainActivity

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.map.journalapp.R
import java.io.ByteArrayOutputStream

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraOpenId: Button
    private lateinit var saveUseButton: Button
    private lateinit var clickImageId: ImageView
    private lateinit var storage: FirebaseStorage
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var imageUri: Uri? = null
    private lateinit var capturedBitmap: Bitmap  // Store the captured image

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Initialize Firebase Storage, Firestore, and Auth
        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Get Buttons and ImageView
        cameraOpenId = findViewById(R.id.camera_button)
        saveUseButton = findViewById(R.id.save_button)
        clickImageId = findViewById(R.id.click_image)

        // Open Camera Button
        cameraOpenId.setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, pic_id)
        }

        // Save and Use Button
        saveUseButton.setOnClickListener {
            saveImageToFirebaseStorage(capturedBitmap)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == pic_id && resultCode == RESULT_OK) {
            val photo = data!!.extras!!["data"] as Bitmap
            capturedBitmap = photo  // Save the bitmap for the "Save and Use" button
            clickImageId.setImageBitmap(photo)
        }
    }

    private fun saveImageToFirebaseStorage(bitmap: Bitmap) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg")

        // Convert Bitmap to ByteArray
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageData = baos.toByteArray()

        // Upload to Firebase Storage
        val uploadTask = storageRef.putBytes(imageData)
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                saveImageUrlToFirestore(downloadUri.toString())
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageUrlToFirestore(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        val userDocumentRef = firestore.collection("users").document(userId)

        val updates = hashMapOf("profileImageUrl" to imageUrl)

        // Update Firestore with the image URL
        userDocumentRef.update(updates as Map<String, Any>).addOnSuccessListener {
            Toast.makeText(this, "Image saved successfully!", Toast.LENGTH_SHORT).show()

            // Send the image URL back to SettingFragment
            val resultIntent = Intent()
            resultIntent.putExtra("imageUrl", imageUrl)
            setResult(RESULT_OK, resultIntent)
            finish()  // Close CameraActivity and return to SettingFragment

        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to save image URL: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val pic_id = 123
    }
}
