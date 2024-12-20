package com.map.journalapp

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.map.journalapp.adapter_model.FolderAdapter
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.FilterFragment
import com.map.journalapp.mainActivity.HomeFragment
import com.map.journalapp.mainActivity.SettingFragment

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var chipGroupTags: ChipGroup
    private val firestore: FirebaseFirestore = Firebase.firestore

    private lateinit var profileIcon: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        setStatusBarColor()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view_top)

        // Get reference to profile icon from toolbar
        profileIcon = toolbar.findViewById(R.id.setting)

        // Check if user is logged in; if not, redirect
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        } else {
            // User is logged in, load user data (profile image) into toolbar icon
            loadUserData()
        }

        profileIcon.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingFragment())
                .addToBackStack(null)
                .commit()
        }

        // Enable hamburger icon for navigation drawer
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // The header view inside navigationView
        val headerView: View = navigationView.getHeaderView(0)
        chipGroupTags = headerView.findViewById(R.id.chipGroupTags)

        // Load tags into ChipGroup
        loadTagsIntoChipGroup()

        // Set navigation item selection
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .addToBackStack(null)
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        // Load HomeFragment at start if no savedInstanceState
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        val logoutButton = findViewById<View>(R.id.btn_logout)
        logoutButton.setOnClickListener {
            logoutUser()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.folderRecycle)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val folderList = listOf("Folder 1", "Folder 2", "Folder 3")
        val folderAdapter = FolderAdapter(folderList) { folderName ->
            Toast.makeText(this, "Clicked on $folderName", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = folderAdapter
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profilePictureUrl = document.getString("profilePicture")
                    if (!profilePictureUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profilePictureUrl)
                            .circleCrop()
                            .placeholder(R.drawable.person)
                            .error(R.drawable.person)
                            .into(profileIcon)
                    }
                    val userName = document.getString("name") ?: "User"

                    // Log the fetched username for debugging
                    Log.d("USER_DATA", "Fetched username: $userName")

                    // Find userNameTextView from the header
                    val headerView: View = navigationView.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.userNameTextView)

                    // Update the greeting with the user's name
                    userNameTextView.text = "Hello, $userName"
                } else {
                    Log.w("USER_DATA", "Document does not exist for userId: $userId")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("USER_DATA", "Error fetching user data: ${e.message}")
            }
    }

    private fun logoutUser() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun loadTagsIntoChipGroup() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("tags")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { result ->
                    chipGroupTags.removeAllViews()
                    for (document in result) {
                        val tagId = document.id
                        val tagName = document.getString("tagName") ?: continue

                        val chip = Chip(this)
                        chip.text = tagName
                        chip.isClickable = true
                        chip.isCheckable = true

                        chipGroupTags.addView(chip)

                        chip.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                onTagSelected(tagId)
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
        val bundle = Bundle().apply {
            putString("selectedTagId", tagId)
        }
        val filterFragment = FilterFragment()
        filterFragment.arguments = bundle

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, filterFragment)
            .addToBackStack(null)
            .commit()

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
}