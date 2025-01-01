// File: MainActivity.kt
package com.map.journalapp

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.map.journalapp.adapter_model.Folder
import com.map.journalapp.adapter_model.FolderAdapter
import com.map.journalapp.databinding.ActivityMainBinding
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.FilterFragment
import com.map.journalapp.mainActivity.HomeFragment
import com.map.journalapp.mainActivity.SettingFragment
import com.map.journalapp.mainActivity.SettingFragment.OnProfileImageUpdatedListener
import com.map.journalapp.write.EachFolderFragment
import java.security.MessageDigest

/**
 * The main activity of the application, handling navigation and user interactions.
 */
class MainActivity : AppCompatActivity(), OnProfileImageUpdatedListener {

    // 1) Firebase Auth
    private lateinit var auth: FirebaseAuth

    // 2) Drawer and NavigationViews
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationViewTop: NavigationView
    private lateinit var navigationViewBottom: NavigationView

    // 3) ChipGroup for tags
    private lateinit var chipGroupTags: ChipGroup

    // 4) Firestore
    private val firestore: FirebaseFirestore = Firebase.firestore

    // 5) Profile Icon in Toolbar
    private lateinit var profileIcon: ImageButton

    // 6) Folder stuff
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var folderRecyclerView: RecyclerView
    private var folderList: MutableList<Folder> = mutableListOf()

    // 7) View Binding
    private lateinit var binding: ActivityMainBinding

    // 8) “New Folder” button
    private lateinit var newFolderButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate & set layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            // If not logged in, go to Login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationViewTop = findViewById(R.id.nav_view_top)
        navigationViewBottom = findViewById(R.id.nav_view_bottom)

        // Profile icon
        profileIcon = toolbar.findViewById(R.id.setting)
        profileIcon.setOnClickListener {
            // Open SettingFragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingFragment())
                .addToBackStack(null)
                .commit()
        }

        // Hamburger icon -> open/close drawer
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // ChipGroup (for tags) in the top nav header
        val headerView: View = navigationViewTop.getHeaderView(0)
        chipGroupTags = headerView.findViewById(R.id.chipGroupTags)

        // Folder RecyclerView
        folderRecyclerView = navigationViewBottom.findViewById(R.id.folderRecycle)
        folderRecyclerView.layoutManager = LinearLayoutManager(this)

        // FolderAdapter: single-click => open EachFolderFragment (with password check if needed)
        folderAdapter = FolderAdapter(emptyList()) { folder ->
            checkFolderPasswordBeforeOpening(folder)
        }
        folderRecyclerView.adapter = folderAdapter

        // Fetch folders from Firestore
        fetchFolders()

        // Load tags -> ChipGroup
        loadTagsIntoChipGroup()

        // Home button
        val homeButton: AppCompatButton = findViewById(R.id.home)
        homeButton.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .addToBackStack(null)
                .commit()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        folderRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {

            private var isClick = false  // Flag to track if it's a click (not drag)

            override fun onInterceptTouchEvent(recyclerView: RecyclerView, e: MotionEvent): Boolean {
                // Check the initial touch down event
                if (e.action == MotionEvent.ACTION_DOWN) {
                    // Start tracking touch
                    isClick = true
                }

                // Check the touch up event
                if (e.action == MotionEvent.ACTION_UP) {
                    if (isClick) {
                        // Close the drawer when an item is clicked (not dragged)
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    // Reset the click flag
                    isClick = false
                }

                return super.onInterceptTouchEvent(recyclerView, e)
            }
        })

        // If no saved instance, load HomeFragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // Logout button
        val logoutButton = findViewById<View>(R.id.btn_logout)
        logoutButton.setOnClickListener {
            logoutUser()
        }

        // "New Folder" button
        newFolderButton = findViewById(R.id.btn_new_folder)
        newFolderButton.setOnClickListener {
            showCreateFolderDialog()
        }

        setupFragmentLifecycleListener()

        setStatusBarIconColor()

        // Load user data (profile, user name)
        loadUserData()
    }

    /**
     * Helper that checks if a folder is password-protected. If it is, prompt for password.
     * If correct or not protected, open the folder.
     */
    private fun checkFolderPasswordBeforeOpening(folder: Folder) {
        if (folder.isPasswordProtected && !folder.password.isNullOrEmpty()) {
            // Prompt for password
            showPasswordPrompt(folder) {
                // If password matched, proceed
                openFolderFragment(folder.id)
            }
        } else {
            // No password needed
            openFolderFragment(folder.id)
        }
    }

    /**
     * Show an AlertDialog to prompt the user for the folder password.
     * If the hashed input matches the stored hash, call onSuccess().
     */
    private fun showPasswordPrompt(folder: Folder, onSuccess: () -> Unit) {
        val builder = AlertDialog.Builder(this, R.style.AlertDialog)
        builder.setTitle("Enter Password")

        val dialogView = layoutInflater.inflate(R.layout.dialog_password_prompt, null)
        builder.setView(dialogView)

        val passwordInput = dialogView.findViewById<EditText>(R.id.editTextPasswordInput)

        builder.setPositiveButton("OK") { _, _ ->
            val inputPassword = passwordInput.text.toString().trim()
            val hashedInput = hashPassword(inputPassword)
            if (hashedInput == folder.password) {
                onSuccess()
            } else {
                Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        // Style the dialog buttons if needed
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.primary_font)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.primary_font)
        )
    }

    /**
     * Opens EachFolderFragment by folderId (after checking password if needed).
     */
    private fun openFolderFragment(folderId: String) {
        val eachFolderFragment = EachFolderFragment.newInstance(folderId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, eachFolderFragment)
            .addToBackStack(null)
            .commit()
    }

    // refresh tag and folder
    private fun setupFragmentLifecycleListener() {
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            when (fragment) {
                is HomeFragment, is SettingFragment, is EachFolderFragment -> {
                    loadTagsIntoChipGroup()
                    fetchFolders()
                }
            }
        }
    }

    /**
     * Fetch folders from Firestore, using "user_id" field
     */
    private fun fetchFolders() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("FOLDER_LOADING", "User ID is null or empty.")
            folderAdapter.updateFolders(folderList)
            return
        }

        firestore.collection("folders")
            .whereEqualTo("user_id", userId)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                folderList.clear()
                if (result.isEmpty) {
                    folderAdapter.updateFolders(folderList)
                    Log.d("FOLDER_LOADING", "No folders found for user.")
                    return@addOnSuccessListener
                }
                for (doc in result) {
                    val folderId = doc.id
                    val fileName = doc.getString("file_name")?.takeIf { it.isNotBlank() }
                        ?: "Untitled Folder"
                    val createdAt = doc.getTimestamp("created_at") ?: Timestamp.now()
                    val userIdDoc = doc.getString("user_id") ?: userId
                    val isPasswordProtected = doc.getBoolean("isPasswordProtected") ?: false
                    val password = doc.getString("password") // hashed password in Firestore

                    val folder = Folder(
                        id = folderId,
                        fileName = fileName,
                        created_at = createdAt,
                        user_id = userIdDoc,
                        isPasswordProtected = isPasswordProtected,
                        password = password
                    )
                    folderList.add(folder)
                }
                folderAdapter.updateFolders(folderList)
                Log.d("FOLDER_LOADING", "Folders loaded: ${folderList.size}")
                folderList.forEach { folder ->
                    Log.d("FOLDER_LOADING", "Folder: '${folder.fileName}'")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load folders: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                Log.e("FOLDER_LOADING", "Failed to load folders: ${e.message}")
            }
    }

    /**
     * Dialog to create a new folder (with optional password).
     */
    private fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialog)
        builder.setTitle("Create New Folder")

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        builder.setView(dialogView)

        val folderNameEditText: EditText = dialogView.findViewById(R.id.editTextFolderName)
        val passwordToggle: Switch = dialogView.findViewById(R.id.switchPassword)
        val passwordEditText: EditText = dialogView.findViewById(R.id.editTextPassword)

        // Initially hide the password input
        passwordEditText.visibility = View.GONE

        passwordToggle.setOnCheckedChangeListener { _, isChecked ->
            passwordEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        builder.setPositiveButton("Create", null)
        builder.setNegativeButton("Cancel", null)

        val alertDialog = builder.create()

        alertDialog.setOnShowListener {
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.primary_font))
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.primary_font))

            positiveButton.setOnClickListener {
                val folderName = folderNameEditText.text.toString().trim()
                val isPasswordProtected = passwordToggle.isChecked
                val password = if (isPasswordProtected) passwordEditText.text.toString() else null

                // Validation
                when {
                    folderName.isEmpty() -> {
                        folderNameEditText.error = "Folder name cannot be empty"
                        folderNameEditText.requestFocus()
                    }
                    isPasswordProtected && password.isNullOrEmpty() -> {
                        passwordEditText.error = "Password cannot be empty"
                        passwordEditText.requestFocus()
                    }
                    else -> {
                        createFolder(folderName, isPasswordProtected, password)
                        alertDialog.dismiss()
                    }
                }
            }

            negativeButton.setOnClickListener {
                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    /**
     * Actually create the folder doc in Firestore (with optional hashed password).
     */
    private fun createFolder(folderName: String, isPasswordProtected: Boolean, password: String?) {
        val userId = auth.currentUser?.uid ?: return
        val hashedPassword = password?.let { hashPassword(it) }

        val folderData = hashMapOf(
            "file_name" to folderName,
            "created_at" to Timestamp.now(),
            "user_id" to userId,
            "isPasswordProtected" to isPasswordProtected,
            "password" to hashedPassword  // store hashed password
        )

        firestore.collection("folders")
            .add(folderData)
            .addOnSuccessListener {
                Toast.makeText(this, "Folder created successfully", Toast.LENGTH_SHORT).show()
                fetchFolders()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create folder: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    /**
     * Load user data (profile pic, name) from Firestore
     */
    fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profilePictureUrl = document.getString("profilePicture")
                    if (!profilePictureUrl.isNullOrEmpty() && !isFinishing) {
                        Glide.with(this)
                            .load(profilePictureUrl)
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // Bypass disk cache
                            .skipMemoryCache(true) // Bypass memory cache
                            .circleCrop()
                            .placeholder(R.drawable.person)
                            .error(R.drawable.person)
                            .into(profileIcon)
                    }
                    val userName = document.getString("name") ?: "User"

                    val headerView: View = navigationViewTop.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.userNameTextView)
                    userNameTextView.text = "Hello, $userName"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    /**
     * Implements the interface method to handle profile image updates.
     * Refreshes the user data by reloading it from Firestore.
     */
    override fun onProfileImageUpdated() {
        loadUserData()
    }

    /**
     * Logout user
     */
    private fun logoutUser() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /**
     * Load tags from Firestore into the ChipGroup
     */
    private fun loadTagsIntoChipGroup() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("tags")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                chipGroupTags.removeAllViews()
                for (document in result) {
                    val tagId = document.id
                    val tagName = document.getString("tagName") ?: continue

                    // Inflate the custom Chip layout (tagchip.xml)
                    val chip = layoutInflater.inflate(R.layout.tagchip, chipGroupTags, false) as Chip
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
                Toast.makeText(this, "Failed to load tags: ${exception.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    /**
     * Called when a tag chip is selected
     */
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

    /**
     * Set the status bar icon color to @color/primary_font based on theme.
     */
    private fun setStatusBarIconColor() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme()

        if (isDarkTheme()) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility = 0
        }
    }

    /**
     * Check if the current theme is dark.
     */
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Hash a password with SHA-256
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
