package com.map.journalapp

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.FilterFragment
import com.map.journalapp.mainActivity.HomeFragment
import com.map.journalapp.mainActivity.SettingFragment
import com.map.journalapp.write.FillJournalActivity
import com.map.journalapp.write.NewNoteActivity

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var chipGroupTags: ChipGroup
    private val firestore: FirebaseFirestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        setStatusBarColor()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Find ChipGroup in the header
        val headerView: View = navigationView.getHeaderView(0)
        chipGroupTags = headerView.findViewById(R.id.chipGroupTags)

        // Enable the hamburger icon
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Load tags dynamically into the ChipGroup
        loadTagsIntoChipGroup()

        // Set a listener for the navigation view
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    // Navigate to HomeFragment
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .addToBackStack(null)
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.setting -> {
                    // Navigate to SettingFragment
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SettingFragment())
                        .addToBackStack(null)
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.new_note -> {
                    // Navigate to NewNoteActivity
                    closeDrawerAndLaunchActivity(NewNoteActivity::class.java)
                    true
                }
                R.id.fill_journal -> {
                    // Navigate to FillJournalActivity
                    closeDrawerAndLaunchActivity(FillJournalActivity::class.java)
                    true
                }
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

    private fun loadTagsIntoChipGroup() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid

        if (userId != null) {
            firestore.collection("tags")
                .whereEqualTo("userId", userId)  // Query only tags associated with the current user
                .get()
                .addOnSuccessListener { result ->
                    chipGroupTags.removeAllViews()  // Clear any previous chips

                    for (document in result) {
                        val tagId = document.id  // Get the tag ID
                        val tagName = document.getString("tagName") ?: continue  // Get tag name

                        // Create a new chip
                        val chip = Chip(this)
                        chip.text = tagName
                        chip.isClickable = true
                        chip.isCheckable = true

                        // Add the chip to the ChipGroup
                        chipGroupTags.addView(chip)

                        // Set click listener for each chip to filter journals by tag ID
                        chip.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                // Pass the tag ID to the filter fragment when the chip is checked
                                onTagSelected(tagId)  // Use tagId here
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to load tags: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onTagSelected(tagId: String) {
        // Navigate to FilterFragment with the selected tag ID
        val bundle = Bundle().apply {
            putString("selectedTagId", tagId)
        }
        val filterFragment = FilterFragment()
        filterFragment.arguments = bundle

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, filterFragment)
            .addToBackStack(null)
            .commit()

        // Close drawer after selection
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun setStatusBarColor() {
        val color = TypedValue().also { theme.resolveAttribute(R.color.white, it, true) }.data
        window.statusBarColor = color
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme()
    }

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun closeDrawerAndLaunchActivity(activityClass: Class<*>) {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        // Delay to ensure DrawerLayout is fully closed
        drawerLayout.postDelayed({
            val intent = Intent(this, activityClass)
            startActivity(intent)
        }, 250)
    }

    override fun onStop() {
        super.onStop()
        // Ensure DrawerLayout is cleaned up completely when MainActivity stops
        if (::drawerLayout.isInitialized) {
            drawerLayout.removeAllViews() // Clean up any view from DrawerLayout
        }
    }
}
