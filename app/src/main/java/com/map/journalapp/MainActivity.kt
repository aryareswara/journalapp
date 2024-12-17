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
import com.map.journalapp.databinding.ActivityMainBinding
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.FilterFragment
import com.map.journalapp.mainActivity.HomeFragment
import com.map.journalapp.mainActivity.SettingFragment

class MainActivity : AppCompatActivity() {
    // using binding
    private lateinit var binding: ActivityMainBinding

    // connect firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // some decoration for UI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var chipGroupTags: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initiliaze firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // check user already login or not
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        // setting up all UI: toolbar, drawer, etc.
        setupUI()

        // load tags dynamically into the ChipGroup
        loadTagsIntoChipGroup()

        // load the HomeFragment when the activity starts
        if (savedInstanceState == null) {
            openFragment(HomeFragment())
        }
    }

    private fun setupUI() {
        setStatusBarColor()

        // setup toolbar and hamburger menu
        val toolbar = binding.toolbar
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            // open or close drawer
            toggleDrawer()
        }

        // setting listener for item in NavigationView
        val navView = binding.navViewTop
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // if home selected
                R.id.home -> {
                    openFragment(HomeFragment())
                    true
                }
                else -> false
            }.also { binding.drawerLayout.closeDrawer(GravityCompat.START) }
        }

        // setting onClick Listener
        binding.setting.setOnClickListener {
            openFragment(SettingFragment())
        }

        // logout button
        binding.btnLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun loadTagsIntoChipGroup() {
        // get the user id
        val userId = auth.currentUser?.uid ?: return

        // get the ChipGroup reference in NavigationView header
        val chipGroupTags = binding.navViewTop.getHeaderView(0).findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTags)

        // get the tags collection from the user
        firestore.collection("tags")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                chipGroupTags.removeAllViews()
                // delete all chip
                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    // generate new chip from the user
                    val chip = Chip(this).apply {
                        text = tagName
                        isClickable = true
                        isCheckable = true
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) onTagSelected(tagId)
                        }
                    }
                    // add chip to ChipGroup
                    chipGroupTags.addView(chip)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load tags: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // open the fragment if the tag selected
    private fun onTagSelected(tagId: String) {
        // transfer data to the fragment
        val bundle = Bundle().apply { putString("selectedTagId", tagId) }
        // create FilterFragment
        val filterFragment = FilterFragment().apply { arguments = bundle }
        openFragment(filterFragment)
    }

    // open fragment
    private fun openFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            // replace the fragmentContainer
            .replace(binding.fragmentContainer.id, fragment)
            // add backstack
            .addToBackStack(null)
            .commit()
    }

    private fun logoutUser() {
        auth.signOut()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            // close the drawer
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // open the drawer
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // set color of the status bar
    private fun setStatusBarColor() {
        val color = TypedValue().also { theme.resolveAttribute(R.color.white, it, true) }.data
        window.statusBarColor = color
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme()
    }

    // check is the dark theme active or not
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
