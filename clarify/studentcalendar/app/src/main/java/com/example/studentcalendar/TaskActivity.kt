package com.example.studentcalendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class TaskActivity : AppCompatActivity() {
    private val tasksList = mutableListOf<Task>()
    private lateinit var tasksContainer: LinearLayout
    private lateinit var userPreferences: UserPreferences
    private var currentUserEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        userPreferences = UserPreferences(this)
        tasksContainer = findViewById(R.id.tasksContainer)

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        // Set up bottom navigation
        NavigationHelper.setupBottomNavigation(
            this,
            findViewById(R.id.bottomNavigation),
            R.id.nav_tasks
        )

        // Set up FAB
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showAddTaskDialog()
        }

        // Set up Add Task button
        findViewById<Button>(R.id.addTaskButton).setOnClickListener {
            showAddTaskDialog()
        }

        // Set up reminder switch
        val reminderSwitch = findViewById<Switch>(R.id.reminderSwitch)
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "Reminders enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Reminders disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up reminder chips
        val reminder15min = findViewById<Chip>(R.id.reminder15min)
        val reminder30min = findViewById<Chip>(R.id.reminder30min)
        val reminder1hour = findViewById<Chip>(R.id.reminder1hour)

        reminder15min.setOnClickListener {
            Toast.makeText(this, "Reminder set for 15 minutes before", Toast.LENGTH_SHORT).show()
            updateReminderChipSelection(reminder15min, reminder30min, reminder1hour)
        }

        reminder30min.setOnClickListener {
            Toast.makeText(this, "Reminder set for 30 minutes before", Toast.LENGTH_SHORT).show()
            updateReminderChipSelection(reminder30min, reminder15min, reminder1hour)
        }

        reminder1hour.setOnClickListener {
            Toast.makeText(this, "Reminder set for 1 hour before", Toast.LENGTH_SHORT).show()
            updateReminderChipSelection(reminder1hour, reminder15min, reminder30min)
        }

        // Get current user email and load tasks
        lifecycleScope.launch {
            currentUserEmail = userPreferences.userEmailFlow.first()
            currentUserEmail?.let { email ->
                userPreferences.getTasksFlow(email).collect { tasks ->
                    tasksList.clear()
                    tasksList.addAll(tasks)
                    refreshTasksView()
                }
            }
        }
    }

    private fun updateReminderChipSelection(selectedChip: Chip, vararg otherChips: Chip) {
        selectedChip.setChipBackgroundColorResource(R.color.purple_light)
        selectedChip.setTextColor(Color.WHITE)

        otherChips.forEach {
            it.setChipBackgroundColorResource(R.color.chip_unselected)
            it.setTextColor(Color.parseColor("#80000000"))
        }
    }

    private fun showAddTaskDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add New Task")

        val dialog = builder.create()

        val taskTitleEditText = dialogView.findViewById<EditText>(R.id.taskTitleEditText)
        val taskLocationEditText = dialogView.findViewById<EditText>(R.id.taskLocationEditText)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val priorityChipGroup = dialogView.findViewById<ChipGroup>(R.id.dialogPriorityChipGroup)
        val addButton = dialogView.findViewById<Button>(R.id.dialogAddButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialogCancelButton)

        // Set default priority
        priorityChipGroup.check(R.id.mediumPriorityChip)

        dateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    dateEditText.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    timeEditText.setText(String.format("%02d:%02d", hour, minute))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        addButton.setOnClickListener {
            val title = taskTitleEditText.text.toString()
            val location = taskLocationEditText.text.toString()
            val date = dateEditText.text.toString()
            val time = timeEditText.text.toString()
            val priority = when (priorityChipGroup.checkedChipId) {
                R.id.highPriorityChip -> "High"
                R.id.lowPriorityChip -> "Low"
                else -> "Medium"
            }

            if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty()) {
                val newTask = Task(title, location, date, time, priority, false)
                tasksList.add(newTask)

                // Save to preferences first, then send broadcast after saving
                lifecycleScope.launch {
                    currentUserEmail?.let { email ->
                        try {
                            userPreferences.saveTasks(tasksList, email)
                            println("DEBUG: Task saved successfully: $title")

                            // Send broadcast AFTER saving is complete
                            runOnUiThread {
                                sendBroadcast(Intent("TASK_UPDATED_ACTION"))
                                refreshTasksView()
                                Toast.makeText(this@TaskActivity, "Task added successfully", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            println("DEBUG: Error saving task: ${e.message}")
                            runOnUiThread {
                                Toast.makeText(this@TaskActivity, "Error saving task", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addTaskToView(task: Task) {
        val taskView = layoutInflater.inflate(R.layout.item_task_card, tasksContainer, false)

        taskView.findViewById<TextView>(R.id.taskTitle).text = task.title
        taskView.findViewById<TextView>(R.id.taskLocation).text = task.location
        taskView.findViewById<TextView>(R.id.taskTime).text = "${task.date} at ${task.time}"

        // Add strikethrough if completed
        if (task.isCompleted) {
            taskView.findViewById<TextView>(R.id.taskTitle).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            taskView.findViewById<TextView>(R.id.taskLocation).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            taskView.findViewById<TextView>(R.id.taskTime).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
        }

        // Set priority indicator color
        val priorityIndicator = taskView.findViewById<View>(R.id.priorityIndicator)
        priorityIndicator.setBackgroundColor(
            when (task.priority) {
                "High" -> Color.parseColor("#FF5252") // Red
                "Medium" -> Color.parseColor("#FFC107") // Amber
                else -> Color.parseColor("#4CAF50") // Green
            }
        )

        // Set priority chip selection
        val priorityChipGroup = taskView.findViewById<ChipGroup>(R.id.priorityChipGroup)
        when (task.priority) {
            "High" -> priorityChipGroup.check(R.id.highPriorityChip)
            "Medium" -> priorityChipGroup.check(R.id.mediumPriorityChip)
            "Low" -> priorityChipGroup.check(R.id.lowPriorityChip)
        }

        // Disable chip interaction (view-only)
        priorityChipGroup.isEnabled = false
        for (i in 0 until priorityChipGroup.childCount) {
            (priorityChipGroup.getChildAt(i) as? Chip)?.isClickable = false
        }

        // Action buttons - Updated mark done button
        taskView.findViewById<Button>(R.id.markDoneButton).setOnClickListener {
            task.isCompleted = true

            // Save to preferences first, then send broadcast after saving
            lifecycleScope.launch {
                currentUserEmail?.let { email ->
                    try {
                        userPreferences.saveTasks(tasksList, email)
                        println("DEBUG: Task marked as done: ${task.title}")

                        // Send broadcast AFTER saving is complete
                        runOnUiThread {
                            sendBroadcast(Intent("TASK_UPDATED_ACTION"))
                            refreshTasksView()
                            Toast.makeText(this@TaskActivity, "Task marked as done", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Error updating task: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(this@TaskActivity, "Error updating task", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        taskView.findViewById<Button>(R.id.editButton).setOnClickListener {
            showEditTaskDialog(task)
        }

        taskView.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete") { _, _ ->
                    tasksList.remove(task)

                    // Save to preferences first, then send broadcast after saving
                    lifecycleScope.launch {
                        currentUserEmail?.let { email ->
                            try {
                                userPreferences.saveTasks(tasksList, email)
                                println("DEBUG: Task deleted: ${task.title}")

                                // Send broadcast AFTER saving is complete
                                runOnUiThread {
                                    sendBroadcast(Intent("TASK_UPDATED_ACTION"))
                                    refreshTasksView()
                                    Toast.makeText(this@TaskActivity, "Task deleted", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                println("DEBUG: Error deleting task: ${e.message}")
                                runOnUiThread {
                                    Toast.makeText(this@TaskActivity, "Error deleting task", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        tasksContainer.addView(taskView)
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Edit Task")

        val dialog = builder.create()

        val taskTitleEditText = dialogView.findViewById<EditText>(R.id.taskTitleEditText)
        val taskLocationEditText = dialogView.findViewById<EditText>(R.id.taskLocationEditText)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val priorityChipGroup = dialogView.findViewById<ChipGroup>(R.id.dialogPriorityChipGroup)
        val saveButton = dialogView.findViewById<Button>(R.id.dialogAddButton).apply {
            text = "Save Changes"
        }

        // Set current values
        taskTitleEditText.setText(task.title)
        taskLocationEditText.setText(task.location)
        dateEditText.setText(task.date)
        timeEditText.setText(task.time)

        when (task.priority) {
            "High" -> priorityChipGroup.check(R.id.highPriorityChip)
            "Medium" -> priorityChipGroup.check(R.id.mediumPriorityChip)
            "Low" -> priorityChipGroup.check(R.id.lowPriorityChip)
        }

        dateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    dateEditText.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    timeEditText.setText(String.format("%02d:%02d", hour, minute))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        saveButton.setOnClickListener {
            val title = taskTitleEditText.text.toString()
            val location = taskLocationEditText.text.toString()
            val date = dateEditText.text.toString()
            val time = timeEditText.text.toString()
            val priority = when (priorityChipGroup.checkedChipId) {
                R.id.highPriorityChip -> "High"
                R.id.lowPriorityChip -> "Low"
                else -> "Medium"
            }

            if (title.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty()) {
                val updatedTask = task.copy(
                    title = title,
                    location = location,
                    date = date,
                    time = time,
                    priority = priority
                )
                val index = tasksList.indexOf(task)
                if (index != -1) {
                    tasksList[index] = updatedTask

                    // Save to preferences first, then send broadcast after saving
                    lifecycleScope.launch {
                        currentUserEmail?.let { email ->
                            try {
                                userPreferences.saveTasks(tasksList, email)
                                println("DEBUG: Task updated: $title")

                                // Send broadcast AFTER saving is complete
                                runOnUiThread {
                                    sendBroadcast(Intent("TASK_UPDATED_ACTION"))
                                    refreshTasksView()
                                    Toast.makeText(this@TaskActivity, "Task updated successfully", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                println("DEBUG: Error updating task: ${e.message}")
                                runOnUiThread {
                                    Toast.makeText(this@TaskActivity, "Error updating task", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<Button>(R.id.dialogCancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun refreshTasksView() {
        tasksContainer.removeAllViews()
        tasksList.forEach { addTaskToView(it) }
    }

    data class Task(
        val title: String,
        val location: String,
        val date: String,
        val time: String,
        val priority: String,
        var isCompleted: Boolean
    )
}