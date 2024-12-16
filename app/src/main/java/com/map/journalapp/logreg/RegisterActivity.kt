package com.map.journalapp.logreg

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.map.journalapp.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {
    // using binding
    private lateinit var binding: ActivityRegisterBinding

    // connect firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // user register
        binding.registerButton.setOnClickListener {
            registerUser()
        }

        // user login
        binding.loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun registerUser() {
        // declare name, email and password
        // the function of .trim() is to delete white space in the beginning and the end of the val
        val name = binding.registerName.text.toString().trim()
        val email = binding.registerEmail.text.toString().trim()
        val password = binding.registerPassword.text.toString().trim()

        // check if the name, email and password are empty or not
        if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid
                            // add user default profile picture
                            val defaultProfileImageUrl = "https://firebasestorage.googleapis.com/v0/b/journal-app-lec.appspot.com/o/profile_images%2Fgaben.jpg?alt=media&token=1c70c236-6946-43c7-962d-17782130d139"
                            val userData = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "profile_picture" to defaultProfileImageUrl
                            )

                            // Store user data in firestore
                            if (userId != null) {
                                db.collection("users").document(userId)
                                    .set(userData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Register Successful", Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this, LoginActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error storing user data", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                }
        } else {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
        }
    }
}