package com.map.journalapp.logreg

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.map.journalapp.MainActivity
import com.map.journalapp.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    // using binding
    private lateinit var binding: ActivityLoginBinding;
    private lateinit var auth: FirebaseAuth;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize firebase
        auth = FirebaseAuth.getInstance()

        // user login
        binding.loginButton.setOnClickListener {
            loginUser()
        }

        // user register
        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        setStatusBarIconColor()
    }

    private fun loginUser() {
        // declare email and password
        // the function of .trim() is to delete white space in the beginning and the end of the val
        val email = binding.loginEmail.text.toString()
        val password = binding.loginPassword.text.toString()

        // check if the email and password are empty or not
        if (email.isNotEmpty() && password.isNotEmpty()) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Login Failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        } else {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setStatusBarIconColor() {
        // Use the WindowInsetsControllerCompat to control the appearance of status bar icons.
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        // Check if the current theme is dark or light, and set icon color accordingly.
        controller.isAppearanceLightStatusBars = !isDarkTheme()

        // If you need to set the status bar icons to a custom color, you can use a combination of systemUiVisibility
        // and ensure that the text or icons use the primary font color for light or dark themes.
        if (isDarkTheme()) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility = 0 // Reset to default if necessary
        }
    }

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}