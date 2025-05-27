package com.example.studentcalendar

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.example.studentcalendar.databinding.ActivityStudytimerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class studytimer : AppCompatActivity() {
    private lateinit var binding: ActivityStudytimerBinding
    private lateinit var userPreferences: UserPreferences
    private var currentTask: TaskActivity.Task? = null
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 0
    private var totalTimeInMillis: Long = 0
    private var currentSessionType = SESSION_TYPE_FOCUS
    private var completedSessions = 0
    private val maxSessions = 4 // Total sessions before long break
    private var currentUserEmail: String? = null

    // Timer modes
    private var currentTimerMode = TIMER_MODE_POMODORO_25
    private val timerModes = mapOf(
        TIMER_MODE_POMODORO_25 to TimerMode(25 * 60 * 1000L, 5 * 60 * 1000L, 15 * 60 * 1000L),
        TIMER_MODE_POMODORO_50 to TimerMode(50 * 60 * 1000L, 10 * 60 * 1000L, 20 * 60 * 1000L),
        TIMER_MODE_CUSTOM to TimerMode(30 * 60 * 1000L, 5 * 60 * 1000L, 10 * 60 * 1000L)
    )

    companion object {
        const val SESSION_TYPE_FOCUS = "FOCUS"
        const val SESSION_TYPE_SHORT_BREAK = "SHORT_BREAK"
        const val SESSION_TYPE_LONG_BREAK = "LONG_BREAK"

        const val TIMER_MODE_POMODORO_25 = "POMODORO_25"
        const val TIMER_MODE_POMODORO_50 = "POMODORO_50"
        const val TIMER_MODE_CUSTOM = "CUSTOM"
    }

    data class TimerMode(
        val focusDuration: Long,
        val shortBreakDuration: Long,
        val longBreakDuration: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudytimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        userPreferences = UserPreferences(this)

        setupUI()
        setupBottomNavigation()
        loadCurrentUserAndTasks()
        setupTimerModeSelection()
        updateSessionIndicators()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.settingsButton.setOnClickListener { showSettingsDialog() }

        binding.changeTaskButton.setOnClickListener {
            showTaskSelectionDialog()
        }

        binding.playPauseButton.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.resetButton.setOnClickListener {
            resetTimer()
        }

        binding.skipButton.setOnClickListener {
            skipSession()
        }
    }

    private fun setupBottomNavigation() {
        NavigationHelper.setupBottomNavigation(
            this,
            binding.bottomNavigation,
            R.id.nav_timer
        )
    }

    private fun loadCurrentUserAndTasks() {
        lifecycleScope.launch {
            // Get current user's email
            currentUserEmail = userPreferences.userEmailFlow.first()

            if (currentUserEmail != null) {
                // Load tasks for the current user
                val tasks = userPreferences.getTasksFlow(currentUserEmail!!).first()
                if (tasks.isNotEmpty()) {
                    currentTask = tasks.first()
                    updateCurrentTaskUI()
                } else {
                    binding.currentTaskCard.visibility = View.GONE
                }
            } else {
                binding.currentTaskCard.visibility = View.GONE
            }
        }
    }

    private fun updateCurrentTaskUI() {
        currentTask?.let { task ->
            binding.currentTaskName.text = task.title
            binding.currentTaskDetails.text = task.date
            binding.currentTaskCard.visibility = View.VISIBLE
        } ?: run {
            binding.currentTaskCard.visibility = View.GONE
        }
    }

    private fun showTaskSelectionDialog() {
        lifecycleScope.launch {
            if (currentUserEmail == null) {
                Toast.makeText(this@studytimer, "No user logged in", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val tasks = userPreferences.getTasksFlow(currentUserEmail!!).first()
            if (tasks.isEmpty()) {
                Toast.makeText(this@studytimer, "No tasks available", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val taskTitles = tasks.map { it.title }.toTypedArray()
            MaterialAlertDialogBuilder(this@studytimer)
                .setTitle("Select Task")
                .setItems(taskTitles) { _, which ->
                    currentTask = tasks[which]
                    updateCurrentTaskUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupTimerModeSelection() {
        binding.timerModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.pomodoro25 -> {
                    currentTimerMode = TIMER_MODE_POMODORO_25
                    resetTimer()
                }
                R.id.pomodoro50 -> {
                    currentTimerMode = TIMER_MODE_POMODORO_50
                    resetTimer()
                }
                R.id.customTimer -> {
                    currentTimerMode = TIMER_MODE_CUSTOM
                    resetTimer()
                }
            }
        }
    }

    private fun startTimer() {
        if (timeLeftInMillis <= 0) {
            // Start a new session
            when (currentSessionType) {
                SESSION_TYPE_FOCUS -> {
                    timeLeftInMillis = timerModes[currentTimerMode]?.focusDuration ?: 25 * 60 * 1000L
                    totalTimeInMillis = timeLeftInMillis
                }
                SESSION_TYPE_SHORT_BREAK -> {
                    timeLeftInMillis = timerModes[currentTimerMode]?.shortBreakDuration ?: 5 * 60 * 1000L
                    totalTimeInMillis = timeLeftInMillis
                }
                SESSION_TYPE_LONG_BREAK -> {
                    timeLeftInMillis = timerModes[currentTimerMode]?.longBreakDuration ?: 15 * 60 * 1000L
                    totalTimeInMillis = timeLeftInMillis
                }
            }
        }

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
                updateProgressBar()
            }

            override fun onFinish() {
                onTimerFinished()
            }
        }.start()

        isTimerRunning = true
        binding.playPauseButton.setImageResource(R.drawable.ic_pause)
    }

    private fun pauseTimer() {
        timer?.cancel()
        isTimerRunning = false
        binding.playPauseButton.setImageResource(R.drawable.ic_play)
    }

    private fun resetTimer() {
        timer?.cancel()
        isTimerRunning = false
        binding.playPauseButton.setImageResource(R.drawable.ic_play)

        when (currentSessionType) {
            SESSION_TYPE_FOCUS -> {
                timeLeftInMillis = timerModes[currentTimerMode]?.focusDuration ?: 25 * 60 * 1000L
                totalTimeInMillis = timeLeftInMillis
            }
            SESSION_TYPE_SHORT_BREAK -> {
                timeLeftInMillis = timerModes[currentTimerMode]?.shortBreakDuration ?: 5 * 60 * 1000L
                totalTimeInMillis = timeLeftInMillis
            }
            SESSION_TYPE_LONG_BREAK -> {
                timeLeftInMillis = timerModes[currentTimerMode]?.longBreakDuration ?: 15 * 60 * 1000L
                totalTimeInMillis = timeLeftInMillis
            }
        }

        updateTimerText()
        updateProgressBar()
    }

    private fun skipSession() {
        timer?.cancel()
        onTimerFinished()
    }

    private fun onTimerFinished() {
        when (currentSessionType) {
            SESSION_TYPE_FOCUS -> {
                completedSessions++
                if (completedSessions >= maxSessions) {
                    currentSessionType = SESSION_TYPE_LONG_BREAK
                    completedSessions = 0
                } else {
                    currentSessionType = SESSION_TYPE_SHORT_BREAK
                }
            }
            SESSION_TYPE_SHORT_BREAK, SESSION_TYPE_LONG_BREAK -> {
                currentSessionType = SESSION_TYPE_FOCUS
            }
        }

        updateSessionUI()
        updateSessionIndicators()
        resetTimer()
    }

    private fun updateTimerText() {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftInMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftInMillis) -
                TimeUnit.MINUTES.toSeconds(minutes)

        binding.timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateProgressBar() {
        val progress = ((totalTimeInMillis - timeLeftInMillis).toFloat() / totalTimeInMillis.toFloat()) * 100
        binding.circularProgressBar.progress = progress.toInt()
    }

    private fun updateSessionUI() {
        binding.sessionTypeLabel.text = when (currentSessionType) {
            SESSION_TYPE_FOCUS -> "FOCUS SESSION"
            SESSION_TYPE_SHORT_BREAK -> "SHORT BREAK"
            SESSION_TYPE_LONG_BREAK -> "LONG BREAK"
            else -> ""
        }

        binding.nextSessionInfo.text = when (currentSessionType) {
            SESSION_TYPE_FOCUS -> {
                if (completedSessions + 1 >= maxSessions) {
                    "Next: Long break"
                } else {
                    "Next: Short break"
                }
            }
            SESSION_TYPE_SHORT_BREAK, SESSION_TYPE_LONG_BREAK -> "Next: Focus session"
            else -> ""
        }
    }

    private fun updateSessionIndicators() {
        val indicators = listOf(
            binding.session1,
            binding.session2,
            binding.currentSession,
            binding.session4,
            binding.session5
        )

        indicators.forEachIndexed { index, view ->
            when {
                index < completedSessions -> {
                    // Completed session
                    view.setBackgroundResource(R.drawable.completed_session_indicator)
                }
                index == completedSessions -> {
                    // Current session
                    view.setBackgroundResource(R.drawable.current_session_indicator)
                }
                else -> {
                    // Future session
                    view.setBackgroundResource(R.drawable.future_session_indicator)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Timer Settings")
            .setMessage("Configure your timer preferences")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}