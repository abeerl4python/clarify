package com.example.studentcalendar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.studentcalendar.databinding.ActivitySettingsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        // Initialize UI components
        initViews()

        // Set up click listeners
        setupClickListeners()

        // Set up bottom navigation
        setupBottomNavigation()

        // Load user data
        loadUserData()
    }

    private fun initViews() {
        // Initialize any additional view components if needed
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            logoutUser()
        }

        binding.layoutEditName.setOnClickListener {
            showNameEditDialog()
        }

        binding.layoutEditEmail.setOnClickListener {
            showEmailEditDialog()
        }

        binding.btnSettings.setOnClickListener {
            // Handle settings button click if needed
        }
    }

    private fun setupBottomNavigation() {
        // Find the bottom navigation view and set it up
        findViewById<BottomNavigationView>(R.id.bottomNavigation)?.let { bottomNav ->
            NavigationHelper.setupBottomNavigation(
                this,
                bottomNav,
                R.id.nav_profile
            )
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                // Get current user data from preferences
                val name = userPreferences.userNameFlow.first()
                val email = userPreferences.userEmailFlow.first()

                Log.d("SettingsActivity", "Loaded user data - Name: $name, Email: $email")

                // Update UI with the loaded values
                updateUserInfo(name, email)

                // Set up ongoing observation of changes
                observeUserDataChanges()

            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error loading user data", e)
                // Fallback to default values if there's an error
                updateUserInfo(null, null)
            }
        }
    }

    private fun updateUserInfo(name: String?, email: String?) {
        runOnUiThread {
            name?.let {
                binding.tvUserName.text = it
                binding.etName.text = it
                binding.tvUserGreeting.text = "Hi, $it"
            } ?: run {
                // Default values if name is null
                binding.tvUserName.text = "User"
                binding.etName.text = "User"
                binding.tvUserGreeting.text = "Hi there"
            }

            email?.let {
                binding.tvUserEmail.text = it
                binding.tvCurrentEmail.text = it
            } ?: run {
                // Default value if email is null
                binding.tvUserEmail.text = "No email provided"
                binding.tvCurrentEmail.text = "No email provided"
            }
        }
    }

    private fun observeUserDataChanges() {
        lifecycleScope.launch {
            userPreferences.userNameFlow.collect { newName ->
                newName?.let {
                    binding.tvUserName.text = it
                    binding.etName.text = it
                    binding.tvUserGreeting.text = "Hi, $it"
                }
            }
        }

        lifecycleScope.launch {
            userPreferences.userEmailFlow.collect { newEmail ->
                newEmail?.let {
                    binding.tvUserEmail.text = it
                    binding.tvCurrentEmail.text = it
                }
            }
        }
    }

    private fun showNameEditDialog() {
        // Implement name edit dialog
        // After successful edit, update preferences with:
        // userPreferences.saveName(newName)
    }

    private fun showEmailEditDialog() {
        // Implement email edit dialog
        // After successful edit, update preferences with:
        // userPreferences.saveCredentials(newEmail, password)
    }

    private fun logoutUser() {
        lifecycleScope.launch {
            userPreferences.clearLoginData()
            redirectToLogin()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh user data when returning to the activity
        loadUserData()
    }
}