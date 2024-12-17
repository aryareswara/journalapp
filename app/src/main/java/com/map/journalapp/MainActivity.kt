package com.map.journalapp

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.map.journalapp.adapter_model.FolderAdapter
import com.map.journalapp.adapter_model.JournalEntry
import com.map.journalapp.adapter_model.JournalSelectionAdapter
import com.map.journalapp.databinding.ActivityMainBinding
import com.map.journalapp.logreg.LoginActivity
import com.map.journalapp.mainActivity.FilterFragment
import com.map.journalapp.mainActivity.HomeFragment
import com.map.journalapp.mainActivity.SettingFragment
import com.map.journalapp.model.Folder
import java.security.MessageDigest
import java.util.Date

class MainActivity : AppCompatActivity() {

    // Firebase Authentication instance
    private lateinit var auth: FirebaseAuth

    // Drawer layout and navigation views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationViewTop: NavigationView
    private lateinit var navigationViewBottom: NavigationView

    // ChipGroup for tags
    private lateinit var chipGroupTags: ChipGroup

    // Firestore instance
    private val firestore: FirebaseFirestore = Firebase.firestore

    // Profile icon in the toolbar
    private lateinit var profileIcon: ImageButton

    // Adapter and RecyclerView for folders
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var folderRecyclerView: RecyclerView
    private var folderList: MutableList<Folder> = mutableListOf()

    // View Binding instance
    private lateinit var binding: ActivityMainBinding

    // Button to create a new folder
    private lateinit var newFolderButton: Button

    // List to hold journal entries
    private var journalEntries: MutableList<JournalEntry> = mutableListOf()
    // private lateinit var journalAdapter: JournalAdapter // If you have one

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // **Initialize View Binding**
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // **Initialize Firebase Auth**
        auth = FirebaseAuth.getInstance()

        // **Set Status Bar Color**
        setStatusBarColor()

        // **Initialize Toolbar**
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // **Initialize DrawerLayout and NavigationViews**
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationViewTop = findViewById(R.id.nav_view_top)
        navigationViewBottom = findViewById(R.id.nav_view_bottom)

        // **Initialize Profile Icon**
        profileIcon = toolbar.findViewById(R.id.setting)

        // **Check User Authentication**
        if (auth.currentUser == null) {
            // If user is not authenticated, redirect to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        } else {
            // User is logged in, load user data (profile image and name)
            loadUserData()
        }

        // **Set Click Listener for Profile Icon**
        profileIcon.setOnClickListener {
            // Open SettingFragment when profile icon is clicked
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingFragment())
                .addToBackStack(null)
                .commit()
        }

        // **Enable Hamburger Icon for Navigation Drawer**
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationOnClickListener {
            // Toggle the navigation drawer when hamburger icon is clicked
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // **Initialize ChipGroup from nav_view_top's Header**
        val headerView: View = navigationViewTop.getHeaderView(0)
        chipGroupTags = headerView.findViewById(R.id.chipGroupTags)

        // **Initialize Folder RecyclerView from nav_view_bottom**
        folderRecyclerView = navigationViewBottom.findViewById(R.id.folderRecycle)
        folderRecyclerView.layoutManager = LinearLayoutManager(this)
        folderRecyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        folderAdapter = FolderAdapter(folderList, { folder ->
            // Handle folder click
            handleFolderClick(folder)
        }, { folder ->
            // Handle folder long click to add journals
            handleFolderLongClick(folder)
        })
        folderRecyclerView.adapter = folderAdapter

        // **Fetch Folders from Firestore**
        fetchFolders()

        // **Load Tags into ChipGroup**
        loadTagsIntoChipGroup()

        // **Set Navigation Item Selection Listener for nav_view_top**
        navigationViewTop.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.home -> {
                    // Open HomeFragment when 'Home' is selected
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

        // **Load HomeFragment at Start if No Saved Instance**
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // **Initialize and Set Listener for Logout Button**
        val logoutButton = findViewById<View>(R.id.btn_logout)
        logoutButton.setOnClickListener {
            // Log out the user when logout button is clicked
            logoutUser()
        }

        // **Initialize and Set Listener for "New Folder" Button**
        newFolderButton = findViewById(R.id.btn_new_folder)
        newFolderButton.setOnClickListener {
            // Show dialog to create a new folder when button is clicked
            showCreateFolderDialog()
        }
    }

    /**
     * Fetches folders from Firestore for the current authenticated user.
     */
    private fun fetchFolders() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("FOLDER_FETCH", "User ID is null.")
            return
        }
        Log.d("FOLDER_FETCH", "Fetching folders for user_id: $userId")

        firestore.collection("folders")
            .whereEqualTo("user_id", userId)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                folderList.clear()
                if (result.isEmpty) {
                    Log.d("FOLDER_FETCH", "No folders found for user_id: $userId")
                    Toast.makeText(this, "No folders available.", Toast.LENGTH_SHORT).show()
                    folderAdapter.updateFolders(folderList) // Clear the adapter
                    return@addOnSuccessListener
                }
                for (document in result) {
                    // Convert Firestore document to Folder object and add to list
                    val folder = document.toObject(Folder::class.java).copy(id = document.id)
                    Log.d("FOLDER_FETCH", "Fetched folder: ${folder.fileName} (ID: ${folder.id})")
                    folderList.add(folder)
                }
                folderAdapter.updateFolders(folderList)
                Log.d("FOLDER_FETCH", "Total folders fetched: ${folderList.size}")
            }
            .addOnFailureListener { exception ->
                // Handle errors during fetching folders
                Toast.makeText(this, "Failed to load folders: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e("FOLDER_FETCH", "Error fetching folders: ${exception.message}", exception)
            }
    }

    /**
     * Displays a dialog to create a new folder with optional password protection.
     */
    private fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New Folder")

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_create_folder, null)
        builder.setView(dialogView)

        val folderNameEditText: EditText = dialogView.findViewById(R.id.editTextFolderName)
        val passwordToggle: Switch = dialogView.findViewById(R.id.switchPassword)
        val passwordEditText: EditText = dialogView.findViewById(R.id.editTextPassword)

        // Initially hide password field
        passwordEditText.visibility = View.GONE

        // Toggle visibility of password field based on switch
        passwordToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                passwordEditText.visibility = View.VISIBLE
            } else {
                passwordEditText.visibility = View.GONE
            }
        }

        // Handle Create and Cancel buttons
        builder.setPositiveButton("Create") { dialog, _ ->
            val folderName = folderNameEditText.text.toString().trim()
            val isPasswordProtected = passwordToggle.isChecked
            val password = if (isPasswordProtected) {
                passwordEditText.text.toString()
            } else {
                null
            }

            // Validate inputs
            if (folderName.isEmpty()) {
                Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
            } else if (isPasswordProtected && password.isNullOrEmpty()) {
                Toast.makeText(this, "Password cannot be empty for password-protected folder", Toast.LENGTH_SHORT).show()
            } else {
                // Create folder with provided details
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
     * Creates a new folder in Firestore with optional password protection.
     *
     * @param folderName The name of the folder to create.
     * @param isPasswordProtected Boolean indicating if the folder is password-protected.
     * @param password The password for the folder, if password-protected.
     */
    private fun createFolder(folderName: String, isPasswordProtected: Boolean, password: String?) {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("FOLDER_CREATE", "User ID is null.")
            return
        }

        // Hash the password if it's provided
        val hashedPassword = password?.let { hashPassword(it) }

        // Prepare folder data
        val folder = hashMapOf(
            "file_name" to folderName,
            "created_at" to Timestamp.now(),
            "user_id" to userId,
            "isPasswordProtected" to isPasswordProtected,
            "password" to hashedPassword
        )

        // Add folder to Firestore
        firestore.collection("folders")
            .add(folder)
            .addOnSuccessListener { documentReference ->
                Log.d("FOLDER_CREATE", "Folder created with ID: ${documentReference.id}")
                Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                // Refresh folder list
                fetchFolders()
            }
            .addOnFailureListener { e ->
                // Handle errors during folder creation
                Toast.makeText(this, "Failed to create folder: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FOLDER_CREATE", "Error creating folder", e)
            }
    }

    /**
     * Handles the click event on a folder.
     *
     * @param folder The folder that was clicked.
     */
    private fun handleFolderClick(folder: Folder) {
        Log.d("FOLDER_CLICK", "Folder clicked: ${folder.fileName} (ID: ${folder.id})")
        if (folder.isPasswordProtected) {
            // If folder is password-protected, prompt for password
            showPasswordPrompt(folder) { isAuthenticated ->
                if (isAuthenticated) {
                    // Open folder if authentication is successful
                    Log.d("FOLDER_CLICK", "Password authenticated for folder: ${folder.fileName}")
                    openFolder(folder)
                } else {
                    // Inform user of incorrect password
                    Log.d("FOLDER_CLICK", "Password authentication failed for folder: ${folder.fileName}")
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Open folder directly if not password-protected
            openFolder(folder)
        }
    }

    /**
     * Opens the selected folder. Implement your logic here to display journals inside the folder.
     *
     * @param folder The folder to open.
     */
    private fun openFolder(folder: Folder) {
        // Implement the logic to open the folder, e.g., show notes inside
        // For example, start a new activity or replace fragment
        // Here, show a Toast
        Toast.makeText(this, "Opening folder: ${folder.fileName}", Toast.LENGTH_SHORT).show()
        Log.d("FOLDER_OPEN", "Opening folder: ${folder.fileName} (ID: ${folder.id})")
        // TODO: Implement folder opening (e.g., open a new fragment or activity to display journal entries)
    }

    /**
     * Prompts the user to enter the folder password.
     *
     * @param folder The folder requiring password authentication.
     * @param callback Callback function to handle authentication result.
     */
    private fun showPasswordPrompt(folder: Folder, callback: (Boolean) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Folder Password")

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_password_prompt, null)
        builder.setView(dialogView)

        val passwordEditText: EditText = dialogView.findViewById(R.id.editTextPasswordInput)

        // Handle OK and Cancel buttons
        builder.setPositiveButton("OK") { dialog, _ ->
            val enteredPassword = passwordEditText.text.toString()
            val enteredHashedPassword = hashPassword(enteredPassword)
            if (enteredHashedPassword == folder.password) {
                Log.d("PASSWORD_PROMPT", "Password correct for folder: ${folder.fileName}")
                callback(true)
            } else {
                Log.d("PASSWORD_PROMPT", "Password incorrect for folder: ${folder.fileName}")
                callback(false)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            callback(false)
            dialog.cancel()
        }

        builder.show()
    }

    /**
     * Handles the long-click event on a folder to add existing journals.
     *
     * @param folder The folder that was long-clicked.
     */
    private fun handleFolderLongClick(folder: Folder) {
        Log.d("FOLDER_LONG_CLICK", "Folder long-clicked: ${folder.fileName} (ID: ${folder.id})")
        if (folder.isPasswordProtected) {
            // If folder is password-protected, prompt for password
            showPasswordPrompt(folder) { isAuthenticated ->
                if (isAuthenticated) {
                    // Proceed to select journals if authentication is successful
                    Log.d("FOLDER_LONG_CLICK", "Password authenticated for adding journals to folder: ${folder.fileName}")
                    showSelectJournalsDialog(folder)
                } else {
                    // Inform user of incorrect password
                    Log.d("FOLDER_LONG_CLICK", "Password authentication failed for adding journals to folder: ${folder.fileName}")
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Proceed to select journals directly if not password-protected
            showSelectJournalsDialog(folder)
        }
    }

    /**
     * Displays a dialog to select existing journals to add to the specified folder.
     *
     * @param folder The folder to which journals will be added.
     */
    private fun showSelectJournalsDialog(folder: Folder) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Journals to Add to ${folder.fileName}")

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_select_journals, null)
        builder.setView(dialogView)

        val recyclerViewJournals: RecyclerView = dialogView.findViewById(R.id.recyclerViewJournalsAddToFolder)
        recyclerViewJournals.layoutManager = LinearLayoutManager(this)
        recyclerViewJournals.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        // Load journals to display in the selection dialog
        loadJournals { journals ->
            if (journals.isEmpty()) {
                Log.d("SELECT_JOURNALS", "No journals available to add for user_id: ${auth.currentUser?.uid}")
                Toast.makeText(this, "No journals available to add.", Toast.LENGTH_SHORT).show()
                return@loadJournals
            }

            // Initialize the adapter with only title and tags
            val selectionAdapter = JournalSelectionAdapter(journals)
            recyclerViewJournals.adapter = selectionAdapter

            // Handle Add Selected button
            builder.setPositiveButton("Add Selected") { dialog, _ ->
                val selectedJournalIds = selectionAdapter.getSelectedJournalIds()
                if (selectedJournalIds.isEmpty()) {
                    Log.d("SELECT_JOURNALS", "No journals selected to add to folder: ${folder.fileName}")
                    Toast.makeText(this, "No journals selected.", Toast.LENGTH_SHORT).show()
                } else {
                    addJournalsToFolder(folder, selectedJournalIds)
                }
                dialog.dismiss()
            }

            // Handle Cancel button
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

            builder.show()
        }
    }

    /**
     * Adds the selected journals to the specified folder in Firestore.
     *
     * @param folder The folder to which journals will be added.
     * @param journalIds List of journal IDs to add to the folder.
     */
    private fun addJournalsToFolder(folder: Folder, journalIds: List<String>) {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("FOLDER_ADD_JOURNAL", "User ID is null.")
            return
        }

        journalIds.forEach { journalId ->
            // Reference to the journal document
            val journalRef = firestore.collection("journals").document(journalId)

            // Option 1: Add folder_id to journal document
            journalRef.update("folder_id", folder.id)
                .addOnSuccessListener {
                    Log.d("FOLDER_ASSOC", "Journal $journalId associated with folder ${folder.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("FOLDER_ASSOC", "Error associating journal: ${e.message}")
                }

            // Option 2: Add journal reference to folder's subcollection
            val folderJournalRef = firestore.collection("folders")
                .document(folder.id)
                .collection("journals")
                .document(journalId)

            folderJournalRef.set(mapOf("journal_id" to journalId))
                .addOnSuccessListener {
                    Log.d("FOLDER_ADD_JOURNAL", "Journal $journalId added to folder ${folder.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("FOLDER_ADD_JOURNAL", "Error adding journal to folder: ${e.message}")
                }
        }

        Toast.makeText(this, "Selected journals have been added to the folder.", Toast.LENGTH_SHORT).show()
        Log.d("FOLDER_ADD_JOURNAL", "Added journals to folder: ${folder.fileName} (ID: ${folder.id})")
        // Optionally, refresh folder details or perform additional actions
    }

    /**
     * Loads user data such as profile picture and name from Firestore.
     */
    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("USER_DATA", "User ID is null.")
            return
        }
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profilePictureUrl = document.getString("profilePicture")
                    if (!profilePictureUrl.isNullOrEmpty()) {
                        // Load profile picture using Glide
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
                    val headerView: View = navigationViewTop.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.userNameTextView)

                    // Update the greeting with the user's name
                    userNameTextView.text = "Hello, $userName"
                } else {
                    Log.w("USER_DATA", "Document does not exist for userId: $userId")
                }
            }
            .addOnFailureListener { e ->
                // Handle errors during fetching user data
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("USER_DATA", "Error fetching user data: ${e.message}")
            }
    }

    /**
     * Logs out the current user and redirects to the LoginActivity.
     */
    private fun logoutUser() {
        FirebaseAuth.getInstance().signOut()
        Log.d("LOGOUT", "User logged out.")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /**
     * Loads tags from Firestore and adds them to the ChipGroup.
     * **Note:** As per your request, this function remains unchanged.
     */
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
                    // Handle errors during fetching tags
                    Toast.makeText(this, "Failed to load tags: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Inform user if not authenticated
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handles tag selection from the ChipGroup.
     *
     * @param tagId The ID of the selected tag.
     */
    private fun onTagSelected(tagId: String) {
        val bundle = Bundle().apply {
            putString("selectedTagId", tagId)
        }
        val filterFragment = FilterFragment()
        filterFragment.arguments = bundle

        // Replace current fragment with FilterFragment to display filtered journals
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, filterFragment)
            .addToBackStack(null)
            .commit()

        // Close the navigation drawer after selection
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    /**
     * Sets the status bar color based on the current theme.
     */
    private fun setStatusBarColor() {
        val color = TypedValue().also { theme.resolveAttribute(R.color.white, it, true) }.data
        window.statusBarColor = color
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme()
    }

    /**
     * Checks if the current theme is dark.
     *
     * @return Boolean indicating if dark theme is active.
     */
    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Hashes the given password using SHA-256.
     *
     * @param password The password to hash.
     * @return The hashed password as a hexadecimal string.
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * Utility function to get the first 20 words from a given text.
     *
     * @param text The text to extract words from.
     * @return A string containing the first 20 words followed by "..." if applicable.
     */
    private fun getFirst20Words(text: String): String {
        return text.split("\\s+".toRegex()).take(20).joinToString(" ") + "..."
    }

    /**
     * Formats a timestamp (in milliseconds) to a readable date string.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return A formatted date string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = DateFormat.getMediumDateFormat(this)
        return format.format(date)
    }

    /**
     * Loads journals from Firestore and executes the callback with the list of journals.
     *
     * @param callback A function that takes a list of JournalEntry objects.
     */
    private fun loadJournals(callback: (List<JournalEntry>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return

        firestore.collection("journals")
            .whereEqualTo("user_id", userId) // Changed to "user_id" for consistency
            .orderBy("created_at", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result: QuerySnapshot ->
                val fetchedJournals = mutableListOf<JournalEntry>()

                if (result.isEmpty) {
                    Log.d("JOURNAL_FETCH", "No journals found for user_id: $userId")
                    callback(emptyList())
                    return@addOnSuccessListener
                }

                val remainingDocuments = result.size()

                for (document in result) {
                    val journalId = document.id
                    val title = document.getString("title") ?: "No Title"
                    val imageUrl = document.getString("image_url")
                    val tagIds = document.get("tags") as? List<String> ?: listOf()

                    firestore.collection("journals")
                        .document(journalId)
                        .collection("notes")
                        .limit(1)
                        .get()
                        .addOnSuccessListener { noteResult: QuerySnapshot ->
                            var description = "No Notes Available"
                            var fullDescription = description

                            if (noteResult.documents.isNotEmpty()) {
                                fullDescription = noteResult.documents[0].getString("content") ?: "No Notes Available"
                                description = getFirst20Words(fullDescription)
                            }

                            val timestamp = document.getLong("created_at") ?: 0L
                            val formattedDate = formatTimestamp(timestamp)

                            fetchTags(tagIds) { tags: List<String> ->
                                val journalEntry = JournalEntry(
                                    id = journalId,
                                    title = title,
                                    shortDescription = description,
                                    createdAt = formattedDate,
                                    tags = tags,
                                    imageUrl = imageUrl,
                                    fullDescription = fullDescription
                                )
                                fetchedJournals.add(journalEntry)
                                Log.d("JOURNAL_FETCH", "Fetched journal: $title (ID: $journalId)")

                                // Once all documents are processed, invoke the callback
                                if (fetchedJournals.size == remainingDocuments) {
                                    Log.d("JOURNAL_FETCH", "All journals fetched successfully.")
                                    callback(fetchedJournals)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JOURNAL_FETCH", "Error fetching notes for journal $journalId: ${e.message}")
                            // Optionally, handle the failure (e.g., reduce remainingDocuments if tracking)
                        }
                }
            }
            .addOnFailureListener { e ->
                // Handle errors during fetching journals
                e.printStackTrace()
                Toast.makeText(this, "Failed to load journals: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("JOURNAL_FETCH", "Error fetching journals: ${e.message}")
            }
    }

    /**
     * Fetches tag names based on tag IDs from Firestore.
     *
     * @param tagIds List of tag IDs.
     * @param callback A function that takes a list of tag names.
     */
    private fun fetchTags(tagIds: List<String>, callback: (List<String>) -> Unit) {
        if (tagIds.isEmpty()) {
            callback(emptyList())
            return
        }

        // Firestore allows a maximum of 10 elements in a whereIn query
        val chunks = tagIds.chunked(10)

        val tagNameList = mutableListOf<String>()
        val totalChunks = chunks.size
        var processedChunks = 0

        for (chunk in chunks) {
            firestore.collection("tags")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { result: QuerySnapshot ->
                    for (document in result) {
                        val tagName = document.getString("tagName") ?: "Unknown"
                        tagNameList.add(tagName)
                    }
                    processedChunks++
                    if (processedChunks == totalChunks) {
                        callback(tagNameList)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FilterFragment", "Error fetching tags: ${e.message}")
                    // Continue processing remaining chunks even if one fails
                    processedChunks++
                    if (processedChunks == totalChunks) {
                        callback(tagNameList)
                    }
                }
        }
    }
}
