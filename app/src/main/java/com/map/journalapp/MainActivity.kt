package com.map.journalapp

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Check if the user is logged in, if not, redirect to the login page
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Status Bar fit user theme
        setStatusBarColor()

        // Load the HomeFragment when the activity starts
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }

    private fun setStatusBarColor() {
        // Get the color for the status bar
        val color = TypedValue().also { theme.resolveAttribute(R.color.white, it, true) }.data
        window.statusBarColor = color

        // Use WindowInsetsControllerCompat for setting icon colors
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        // Check the current theme mode and set the appropriate icon colors
        if (isDarkTheme()) {
            // Dark theme, use light icons
            controller.isAppearanceLightStatusBars = false
        } else {
            // Light theme, use dark icons
            controller.isAppearanceLightStatusBars = true
        }
    }

    // Helper function to determine if the app is in dark mode
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
