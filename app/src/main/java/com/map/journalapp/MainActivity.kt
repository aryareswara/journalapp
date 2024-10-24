package com.map.journalapp

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.HomeFragment
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.map.journalapp.mainActivity.SettingFragment

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        setStatusBarColor()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout) // Initialize drawerLayout
        navigationView = findViewById(R.id.nav_view) // Initialize navigationView

        // Enable the hamburger icon
        toolbar.setNavigationIcon(R.drawable.ic_menu) // Replace with your icon resource
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Set a listener for the navigation view
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    // Navigate to HomeFragment
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .addToBackStack(null) // Add to back stack
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START) // Close the drawer
                    true
                }
                R.id.setting -> {
                    // Navigate to SettingFragment
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SettingFragment())
                        .addToBackStack(null) // Add to back stack
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START) // Close the drawer
                    true
                }
                // Handle other menu items here if necessary
                else -> false
            }
        }

        // Check if the user is logged in, if not, redirect to the login page
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

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
        controller.isAppearanceLightStatusBars = !isDarkTheme()
    }

    // Helper function to determine if the app is in dark mode
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}