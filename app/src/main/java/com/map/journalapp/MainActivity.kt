package com.map.journalapp

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
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
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
import com.map.journalapp.write.EachFolderFragment
import java.security.MessageDigest
import com.map.journalapp.adapter_model.Folder as FolderModel

class MainActivity : AppCompatActivity() {

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
    private var folderList: MutableList<FolderModel> = mutableListOf()

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
        folderRecyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        // FolderAdapter: single-click => open EachFolderFragment
        folderAdapter = FolderAdapter(emptyList()) { folder ->
            openFolderFragment(folder.id)
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

        setStatusBarIconColor()

        // Load user data (profile, user name)
        loadUserData()
    }

    /**
     * Opens EachFolderFragment by folderId
     */
    private fun openFolderFragment(folderId: String) {
        val eachFolderFragment = EachFolderFragment.newInstance(folderId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, eachFolderFragment)
            .addToBackStack(null)
            .commit()
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
                    // No folders => update adapter with empty list
                    folderAdapter.updateFolders(folderList)
                    Log.d("FOLDER_LOADING", "No folders found for user.")
                    return@addOnSuccessListener
                }
                for (doc in result) {
                    val folderId = doc.id
                    val fileName = doc.getString("file_name")?.takeIf { it.isNotBlank() } ?: "Untitled Folder"
                    val createdAt = doc.getTimestamp("created_at") ?: Timestamp.now()
                    val userIdDoc = doc.getString("user_id") ?: userId
                    val isPasswordProtected = doc.getBoolean("isPasswordProtected") ?: false
                    val password = doc.getString("password")

                    // Construct folder model manually
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
                Toast.makeText(this, "Failed to load folders: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FOLDER_LOADING", "Failed to load folders: ${e.message}")
            }
    }


    /**
     * Dialog to create a new folder
     */
    private fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New Folder")

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_folder, null)
        builder.setView(dialogView)

        val folderNameEditText: EditText = dialogView.findViewById(R.id.editTextFolderName)
        val passwordToggle: Switch = dialogView.findViewById(R.id.switchPassword)
        val passwordEditText: EditText = dialogView.findViewById(R.id.editTextPassword)

        passwordEditText.visibility = View.GONE

        passwordToggle.setOnCheckedChangeListener { _, isChecked ->
            passwordEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        builder.setPositiveButton("Create") { dialog, _ ->
            val folderName = folderNameEditText.text.toString().trim()
            val isPasswordProtected = passwordToggle.isChecked
            val password = if (isPasswordProtected) passwordEditText.text.toString() else null

            if (folderName.isEmpty()) {
                Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
            } else if (isPasswordProtected && password.isNullOrEmpty()) {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                createFolder(folderName, isPasswordProtected, password)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    /**
     * Actually create the folder doc in Firestore
     */
    private fun createFolder(folderName: String, isPasswordProtected: Boolean, password: String?) {
        val userId = auth.currentUser?.uid ?: return
        val hashedPassword = password?.let { hashPassword(it) }

        val folderData = hashMapOf(
            "file_name" to folderName,
            "created_at" to Timestamp.now(),
            "user_id" to userId,  // must match what's in Firestore (not "userId")
            "isPasswordProtected" to isPasswordProtected,
            "password" to hashedPassword
        )

        firestore.collection("folders")
            .add(folderData)
            .addOnSuccessListener {
                Toast.makeText(this, "Folder created successfully", Toast.LENGTH_SHORT).show()
                fetchFolders()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create folder: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Load user data (profile pic, name) from Firestore
     */
    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profilePictureUrl = document.getString("profilePicture")
                    if (!profilePictureUrl.isNullOrEmpty()) {
                        // IMPORTANT: check if activity is finishing before calling Glide
                        if (!isFinishing) {
                            Glide.with(this)
                                .load(profilePictureUrl)
                                .circleCrop()
                                .placeholder(R.drawable.person)
                                .error(R.drawable.person)
                                .into(profileIcon)
                        }
                    }
                    val userName = document.getString("name") ?: "User"

                    val headerView: View = navigationViewTop.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.userNameTextView)
                    userNameTextView.text = "Hello, $userName"
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
            .whereEqualTo("userId", userId) // If your tags store "userId" instead of "user_id"
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

    /**
     * Check if the current theme is dark.
     */
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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
