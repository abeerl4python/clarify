package com.example.studentcalendar

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        userPreferences = UserPreferences(this)

        lifecycleScope.launch {
            userPreferences.isLoggedInFlow.collect { isLoggedIn ->
                if (isLoggedIn) {
                    navigateToMainActivity()
                }
            }
        }

        lifecycleScope.launch {
            userPreferences.rememberMeFlow.collect { rememberMe ->
                if (rememberMe) {
                    userPreferences.userEmailFlow.collect { email ->
                        findViewById<EditText>(R.id.etEmail).setText(email)
                    }
                    findViewById<CheckBox>(R.id.cbRememberMe).isChecked = true
                }
            }
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString()
            val password = findViewById<EditText>(R.id.etPassword).text.toString()
            val rememberMe = findViewById<CheckBox>(R.id.cbRememberMe).isChecked

            if (validateInputs(email, password)) {
                lifecycleScope.launch {
                    // Option 1: If you don't need to save the name during login
                    userPreferences.saveCredentials(email, password)
                    userPreferences.setLoggedIn(true, rememberMe)

                    // Option 2: If you have the name available (uncomment if needed)
                    // val name = "" // Get user's name from somewhere or use empty string
                    // userPreferences.saveLoginData(name, email, password, rememberMe)

                    navigateToMainActivity()
                }
            }
        }

        findViewById<Button>(R.id.btnSignUp).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            Toast.makeText(this, "Forgot password clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            findViewById<EditText>(R.id.etEmail).error = "Email is required"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            findViewById<EditText>(R.id.etEmail).error = "Please enter a valid email"
            return false
        }
        if (password.isEmpty()) {
            findViewById<EditText>(R.id.etPassword).error = "Password is required"
            return false
        }
        if (password.length < 6) {
            findViewById<EditText>(R.id.etPassword).error = "Password must be at least 6 characters"
            return false
        }
        return true
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}