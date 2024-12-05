package com.map.journalapp.logreg

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.auth.User
import com.google.gson.Gson
import com.map.journalapp.MainActivity
import com.map.journalapp.databinding.ActivityLoginBinding
import java.io.File
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.loginButton.setOnClickListener {
            loginUser()
        }

        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = binding.loginEmail.text.toString()
        val password = binding.loginPassword.text.toString()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Start fetching user data
                        fetchUserDataWithFallback(auth.currentUser?.uid ?: "")
                    } else {
                        Toast.makeText(
                            this,
                            "Login Failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun fetchUserDataWithFallback(userId: String) {
        val file = File(filesDir, "user.json")
        val handler = Handler(Looper.getMainLooper())

        // Attempt to read the data from the JSON file with a 2-second timeout
        handler.postDelayed({
            if (file.exists()) {
                try {
                    val userJson = file.readText()
                    val gson = Gson()
                    val user = gson.fromJson(userJson, User::class.java)

                    // If the user is fetched successfully, proceed to the main activity
                    Toast.makeText(this, "User Data Loaded from Local Storage", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } catch (e: IOException) {
                    // If reading the file fails, fall back to Firestore
                    fetchFromFirestore(userId)
                }
            } else {
                // If file does not exist, fall back to Firestore
                fetchFromFirestore(userId)
            }
        }, 2000)  // 2-second timeout for reading from the local JSON
    }

    private fun fetchFromFirestore(userId: String) {
        // Fetch the user data from Firestore
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Toast.makeText(this, "User Data Loaded from Firestore", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    Toast.makeText(this, "User Data Not Found in Firestore", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error fetching user data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
