package com.example.studentcalendar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {
    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        userPreferences = UserPreferences(this)

        // Sign Up Button Click Listener
        findViewById<Button>(R.id.btnSignUp).setOnClickListener {
            val name = findViewById<EditText>(R.id.etFullName).text.toString()
            val email = findViewById<EditText>(R.id.etEmail).text.toString()
            val password = findViewById<EditText>(R.id.etPassword).text.toString()
            val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword).text.toString()

            if (validateInputs(name, email, password, confirmPassword)) {
                lifecycleScope.launch {
                    userPreferences.saveLoginData(
                        name = name,
                        email = email,
                        password = password,
                        rememberMe = true
                    )
                    showSuccessAndNavigate()
                }
            }
        }

        // "Already a Member? Login Now" Click Listener
        findViewById<TextView>(R.id.tvLoginLink).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Back Button Click Listener
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish() // Close current activity and go back
        }
    }

    private fun validateInputs(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (name.isEmpty()) {
            findViewById<TextInputEditText>(R.id.etFullName).error = "Name is required"
            return false
        }
        if (email.isEmpty()) {
            findViewById<TextInputEditText>(R.id.etEmail).error = "Email is required"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            findViewById<TextInputEditText>(R.id.etEmail).error = "Please enter a valid email"
            return false
        }
        if (password.isEmpty()) {
            findViewById<TextInputEditText>(R.id.etPassword).error = "Password is required"
            return false
        }
        if (password.length < 6) {
            findViewById<TextInputEditText>(R.id.etPassword).error = "Password must be at least 6 characters"
            return false
        }
        if (confirmPassword.isEmpty()) {
            findViewById<TextInputEditText>(R.id.etConfirmPassword).error = "Please confirm your password"
            return false
        }
        if (password != confirmPassword) {
            findViewById<TextInputEditText>(R.id.etConfirmPassword).error = "Passwords don't match"
            return false
        }
        return true
    }

    private fun showSuccessAndNavigate() {
        Toast.makeText(this, "Registration successful! Please login", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }
}