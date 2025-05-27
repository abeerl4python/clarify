package com.example.studentcalendar

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var logo: ImageView
    private lateinit var appName: TextView
    private lateinit var progressBar: ProgressBar

    private val splashTimeOut: Long = 3000 // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        initViews()
        startSplashTimer()
    }

    private fun initViews() {
        logo = findViewById(R.id.logo)
        appName = findViewById(R.id.app_name)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun startSplashTimer() {
        Handler(Looper.getMainLooper()).postDelayed({
            // Start your main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, splashTimeOut)
    }

    override fun onBackPressed() {
        // Disable back button on splash screen
        // Do nothing or call super.onBackPressed() if you want default behavior
    }
}