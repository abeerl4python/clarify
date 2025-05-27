package com.example.studentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var currentMonthText: TextView
    private lateinit var prevMonthText: TextView
    private lateinit var nextMonthText: TextView
    private lateinit var prevMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton
    private lateinit var dateContainer: LinearLayout

    private lateinit var calendar: Calendar
    private lateinit var dateFormatter: SimpleDateFormat
    private lateinit var monthFormatter: SimpleDateFormat
    private var selectedDatePosition = -1

    private lateinit var userPreferences: UserPreferences
    private var currentDisplayedDate: String? = null
    private var tasksList: List<TaskActivity.Task> = emptyList()
    private var currentUserEmail: String? = null

    // Views for events display
    private lateinit var eventsContainer: ConstraintLayout
    private lateinit var noEventsText: TextView
    private lateinit var eventsLayout: LinearLayout
    private lateinit var todaySummaryLabel: TextView

    private val taskUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "TASK_UPDATED_ACTION") {
                Handler(Looper.getMainLooper()).postDelayed({
                    loadTasksAndRefresh()
                }, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = UserPreferences(this)
        checkAuthentication()
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter("TASK_UPDATED_ACTION")
        registerReceiver(taskUpdateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        // Force refresh tasks and update display
        println("DEBUG: Activity resumed - refreshing tasks")
        loadTasksAndRefresh()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(taskUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    private fun checkAuthentication() {
        lifecycleScope.launch {
            try {
                val isLoggedIn = userPreferences.isLoggedInFlow.first()
                if (isLoggedIn) {
                    currentUserEmail = userPreferences.userEmailFlow.first()
                    println("DEBUG: Current user email: $currentUserEmail")
                    initializeUI()

                    // Add a small delay to ensure UI is initialized before starting observation
                    kotlinx.coroutines.delay(100)
                    startObservingTasks()
                } else {
                    redirectToLogin()
                }
            } catch (e: Exception) {
                println("DEBUG: Error in authentication check: ${e.message}")
                redirectToLogin()
            }
        }
    }

    private fun startObservingTasks() {
        currentUserEmail?.let { email ->
            lifecycleScope.launch {
                println("DEBUG: Starting to observe tasks for email: $email")
                userPreferences.getTasksFlow(email).collect { tasks ->
                    println("DEBUG: Tasks flow updated - ${tasks.size} tasks received")
                    tasksList = tasks

                    runOnUiThread {
                        updateCalendar()

                        // If no date is selected or it's app startup, select today
                        if (currentDisplayedDate == null) {
                            val todayKey = dateFormatter.format(Calendar.getInstance().time)
                            currentDisplayedDate = todayKey
                            println("DEBUG: Flow update - auto-selecting today: $todayKey")
                        }

                        currentDisplayedDate?.let { date ->
                            println("DEBUG: Flow update - updating events for date: $date")
                            updateEventsForDate(date)
                        }
                    }
                }
            }
        }
    }

    private fun initializeUI() {
        setContentView(R.layout.activity_main)
        initializeViews()
        setupBottomNavigation()
        setupFloatingActionButton()
        setupTimerButtons()
        initializeEventViews()
        loadTasksAndRefresh()
    }

    private fun initializeViews() {
        calendar = Calendar.getInstance()
        dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        currentMonthText = findViewById(R.id.currentMonth)
        prevMonthText = findViewById(R.id.prevMonthText)
        nextMonthText = findViewById(R.id.nextMonthText)
        prevMonthButton = findViewById(R.id.prevMonth)
        nextMonthButton = findViewById(R.id.nextMonth)
        dateContainer = findViewById(R.id.dateContainer)

        prevMonthButton.setOnClickListener { changeMonth(-1) }
        nextMonthButton.setOnClickListener { changeMonth(1) }
    }

    private fun initializeEventViews() {
        // Initialize event-related views with proper error handling
        try {
            eventsContainer = findViewById(R.id.eventsContainer)
            noEventsText = findViewById(R.id.noEventsText)
            eventsLayout = findViewById(R.id.eventsList)
            todaySummaryLabel = findViewById(R.id.todaySummaryLabel)
            println("DEBUG: All event views initialized successfully")
        } catch (e: Exception) {
            println("DEBUG: Error initializing event views: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadTasksAndRefresh() {
        lifecycleScope.launch {
            try {
                currentUserEmail?.let { email ->
                    println("DEBUG: Loading tasks for email: $email")
                    val tasks = userPreferences.getTasksFlow(email).first()
                    tasksList = tasks
                    println("DEBUG: Loaded ${tasks.size} tasks")

                    // Debug all loaded tasks
                    tasks.forEachIndexed { index, task ->
                        println("DEBUG: Task $index: '${task.title}' on '${task.date}' at '${task.time}' - Priority: ${task.priority} - Completed: ${task.isCompleted}")
                    }

                    runOnUiThread {
                        updateCalendar()

                        // Ensure today is selected if no date is currently selected
                        if (currentDisplayedDate == null) {
                            val today = Calendar.getInstance()
                            val todayKey = dateFormatter.format(today.time)
                            currentDisplayedDate = todayKey
                            println("DEBUG: Auto-selecting today: $todayKey")

                            // Find today's position in the calendar
                            val firstDayOfMonth = calendar.clone() as Calendar
                            firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
                            val displayStart = firstDayOfMonth.clone() as Calendar
                            displayStart.add(Calendar.DAY_OF_MONTH, -firstDayOfMonth.get(Calendar.DAY_OF_WEEK) + 1)

                            // Calculate today's position
                            val todayPosition = ((today.timeInMillis - displayStart.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                            if (todayPosition >= 0 && todayPosition < 35) {
                                selectedDatePosition = todayPosition
                                println("DEBUG: Set today's position to: $todayPosition")
                            }
                        }

                        // Update events for the selected date
                        currentDisplayedDate?.let { date ->
                            println("DEBUG: Updating events for selected date: $date")
                            updateEventsForDate(date)
                        }
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading tasks: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun setupBottomNavigation() {
        NavigationHelper.setupBottomNavigation(
            this,
            findViewById(R.id.bottomNavigation),
            R.id.nav_home
        )
    }

    private fun setupFloatingActionButton() {
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            val intent = Intent(this, TaskActivity::class.java).apply {
                putExtra("NEW_TASK", true)
            }
            startActivity(intent)
        }
    }

    private fun setupTimerButtons() {
        findViewById<Button>(R.id.pomodoro25).setOnClickListener { startTimer(25) }
        findViewById<Button>(R.id.pomodoro50).setOnClickListener { startTimer(50) }
        findViewById<Button>(R.id.customTimer).setOnClickListener { showCustomTimerDialog() }
    }

    private fun updateCalendar() {
        currentMonthText.text = monthFormatter.format(calendar.time)

        val prevCalendar = calendar.clone() as Calendar
        prevCalendar.add(Calendar.MONTH, -1)
        prevMonthText.text = SimpleDateFormat("MMM", Locale.getDefault()).format(prevCalendar.time)

        val nextCalendar = calendar.clone() as Calendar
        nextCalendar.add(Calendar.MONTH, 1)
        nextMonthText.text = SimpleDateFormat("MMM", Locale.getDefault()).format(nextCalendar.time)

        val today = Calendar.getInstance()
        val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH)

        dateContainer.removeAllViews()

        val firstDayOfMonth = calendar.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)

        val displayStart = firstDayOfMonth.clone() as Calendar
        displayStart.add(Calendar.DAY_OF_MONTH, -firstDayOfMonth.get(Calendar.DAY_OF_WEEK) + 1)

        for (i in 0 until 35) {
            val dayCalendar = displayStart.clone() as Calendar
            dayCalendar.add(Calendar.DAY_OF_MONTH, i)

            val dateKey = dateFormatter.format(dayCalendar.time)
            val isToday = isCurrentMonth &&
                    dayCalendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                    dayCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    dayCalendar.get(Calendar.MONTH) == today.get(Calendar.MONTH)

            val dateCard = LayoutInflater.from(this).inflate(R.layout.item_date_card, dateContainer, false) as MaterialCardView
            val dayText = dateCard.findViewById<TextView>(R.id.dayText)
            val dayNameText = dateCard.findViewById<TextView>(R.id.dayNameText)
            val eventIndicator = dateCard.findViewById<View>(R.id.eventIndicator)

            dayText.text = dayCalendar.get(Calendar.DAY_OF_MONTH).toString()
            dayNameText.text = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCalendar.time).take(3)

            // Check for events with improved matching
            val hasEvents = tasksList.any { task ->
                val convertedDate = convertTaskDateToCalendarFormat(task.date)
                val matches = convertedDate == dateKey
                if (matches) {
                    println("DEBUG: Date $dateKey has task: ${task.title}")
                }
                matches
            }

            eventIndicator.visibility = if (hasEvents) View.VISIBLE else View.GONE
            println("DEBUG: Date $dateKey - hasEvents: $hasEvents, indicator visibility: ${eventIndicator.visibility}")

            when {
                isToday -> {
                    dateCard.setCardBackgroundColor(Color.parseColor("#573376"))
                    dayText.setTextColor(Color.WHITE)
                    dayNameText.setTextColor(Color.WHITE)
                    // Auto-select today if no date is selected
                    if (selectedDatePosition == -1 || currentDisplayedDate == null) {
                        selectedDatePosition = i
                        currentDisplayedDate = dateKey
                        println("DEBUG: Auto-selected today at position $i with date $dateKey")
                    }
                }
                i == selectedDatePosition -> {
                    dateCard.setCardBackgroundColor(Color.parseColor("#B3A1DE"))
                    dayText.setTextColor(Color.WHITE)
                    dayNameText.setTextColor(Color.WHITE)
                }
                else -> {
                    dateCard.setCardBackgroundColor(Color.parseColor("#F8F8F8"))
                    dayText.setTextColor(if (hasEvents) Color.parseColor("#573376") else Color.parseColor("#717171"))
                    dayNameText.setTextColor(if (hasEvents) Color.parseColor("#573376") else Color.parseColor("#80000000"))
                }
            }

            dateCard.setOnClickListener {
                selectedDatePosition = i
                currentDisplayedDate = dateKey
                println("DEBUG: Selected date: $dateKey at position $i")
                updateCalendar()
                updateEventsForDate(dateKey)
            }

            dateContainer.addView(dateCard)
        }
    }

    private fun changeMonth(monthsToAdd: Int) {
        calendar.add(Calendar.MONTH, monthsToAdd)
        selectedDatePosition = -1
        currentDisplayedDate = null
        updateCalendar()
        // Clear events display when changing months
        updateEventsDisplay(emptyList())
    }

    private fun updateEventsForDate(dateKey: String) {
        println("DEBUG: === UPDATING EVENTS FOR DATE: $dateKey ===")
        println("DEBUG: Total tasks available: ${tasksList.size}")

        // Debug all tasks
        tasksList.forEachIndexed { index, task ->
            val convertedDate = convertTaskDateToCalendarFormat(task.date)
            println("DEBUG: Task $index: '${task.title}' - Original: '${task.date}', Converted: '$convertedDate', Matches: ${convertedDate == dateKey}")
        }

        val filteredTasks = tasksList.filter { task ->
            val convertedDate = convertTaskDateToCalendarFormat(task.date)
            val matches = convertedDate == dateKey
            if (matches) {
                println("DEBUG: ✓ MATCHED Task: '${task.title}' for date $dateKey")
            }
            matches
        }

        println("DEBUG: Found ${filteredTasks.size} tasks for date $dateKey")

        // Update tasks summary
        updateTasksSummary(filteredTasks)

        // Convert tasks to events
        val dateEvents = filteredTasks.map { task ->
            Event(
                title = task.title,
                location = task.location.ifEmpty { "No location" },
                time = task.time,
                color = when (task.priority) {
                    "High" -> "#FF5252"
                    "Medium" -> "#FFC107"
                    else -> "#4CAF50"
                },
                isCompleted = task.isCompleted
            )
        }

        println("DEBUG: Created ${dateEvents.size} events for display")
        updateEventsDisplay(dateEvents)
    }

    private fun updateEventsDisplay(events: List<Event>) {
        println("DEBUG: === UPDATING EVENTS DISPLAY ===")
        println("DEBUG: updateEventsDisplay called with ${events.size} events")

        try {
            // Check if views are initialized
            if (!::eventsContainer.isInitialized || !::noEventsText.isInitialized || !::eventsLayout.isInitialized) {
                println("DEBUG: Event views not initialized, trying to initialize...")
                initializeEventViews()
            }

            // Clear previous events
            eventsLayout.removeAllViews()

            if (events.isNotEmpty()) {
                // Show events container, hide no events text
                eventsContainer.visibility = View.VISIBLE
                noEventsText.visibility = View.GONE

                println("DEBUG: Showing ${events.size} events")

                events.forEach { event ->
                    println("DEBUG: Adding event view for: ${event.title}")
                    addEventToLayout(event)
                }
            } else {
                // Show no events text, hide events container
                eventsContainer.visibility = View.GONE
                noEventsText.visibility = View.VISIBLE

                // Update the no events message to be more specific
                val selectedDateText = if (isSelectedDateToday()) {
                    "No tasks scheduled for today"
                } else {
                    "No tasks scheduled for this day"
                }
                noEventsText.text = selectedDateText
                println("DEBUG: No events to display - showing no events text")
            }

            // Update progress
            updateProgress(events)

            // Update date label
            updateDateLabel()

            println("DEBUG: Final state - Events container visibility: ${eventsContainer.visibility}")
            println("DEBUG: Final state - No events text visibility: ${noEventsText.visibility}")
            println("DEBUG: Final state - Events layout child count: ${eventsLayout.childCount}")

        } catch (e: Exception) {
            println("DEBUG: Error in updateEventsDisplay: ${e.message}")
            e.printStackTrace()
            // Create fallback display
            createFallbackEventDisplay(events)
        }
    }

    private fun addEventToLayout(event: Event) {
        try {
            // Try to inflate the proper layout first
            val eventView = LayoutInflater.from(this).inflate(R.layout.item_event, eventsLayout, false)

            // Set event details
            eventView.findViewById<TextView>(R.id.eventTitle).text = event.title
            eventView.findViewById<TextView>(R.id.eventLocation).text = event.location
            eventView.findViewById<TextView>(R.id.eventTime).text = event.time

            // Set background color based on priority
            val eventCard = eventView.findViewById<MaterialCardView>(R.id.eventCard)
            eventCard.setCardBackgroundColor(Color.parseColor(event.color))

            // Add strikethrough if completed
            if (event.isCompleted) {
                eventView.findViewById<TextView>(R.id.eventTitle).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                eventView.findViewById<TextView>(R.id.eventLocation).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                eventView.findViewById<TextView>(R.id.eventTime).paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                eventView.findViewById<TextView>(R.id.eventTitle).paintFlags = 0
                eventView.findViewById<TextView>(R.id.eventLocation).paintFlags = 0
                eventView.findViewById<TextView>(R.id.eventTime).paintFlags = 0
            }

            eventView.setOnClickListener {
                openTaskDetails(event.title, event.location, event.time)
            }

            eventsLayout.addView(eventView)
            println("DEBUG: Successfully added event view for: ${event.title}")

        } catch (e: Exception) {
            println("DEBUG: Error inflating event view: ${e.message}")
            // Create a simple fallback view
            createSimpleEventView(event)
        }
    }

    private fun createSimpleEventView(event: Event) {
        val eventView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor(event.color))

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 16
            this.layoutParams = layoutParams
        }

        val titleView = TextView(this).apply {
            text = event.title
            textSize = 16f
            setTextColor(Color.WHITE)
            setPaintFlags(if (event.isCompleted) Paint.STRIKE_THRU_TEXT_FLAG else 0)
        }

        val locationView = TextView(this).apply {
            text = event.location
            textSize = 14f
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setPaintFlags(if (event.isCompleted) Paint.STRIKE_THRU_TEXT_FLAG else 0)
        }

        val timeView = TextView(this).apply {
            text = event.time
            textSize = 14f
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setPaintFlags(if (event.isCompleted) Paint.STRIKE_THRU_TEXT_FLAG else 0)
        }

        eventView.addView(titleView)
        eventView.addView(locationView)
        eventView.addView(timeView)

        eventView.setOnClickListener {
            openTaskDetails(event.title, event.location, event.time)
        }

        eventsLayout.addView(eventView)
        println("DEBUG: Added simple event view for: ${event.title}")
    }

    private fun createFallbackEventDisplay(events: List<Event>) {
        try {
            eventsLayout.removeAllViews()

            if (events.isNotEmpty()) {
                val fallbackText = TextView(this).apply {
                    text = "Found ${events.size} events for this date:\n" +
                            events.joinToString("\n") { "• ${it.title} at ${it.time}" }
                    textSize = 14f
                    setPadding(32, 32, 32, 32)
                    setTextColor(Color.parseColor("#7E749E"))
                }
                eventsLayout.addView(fallbackText)
                eventsContainer.visibility = View.VISIBLE
                noEventsText.visibility = View.GONE
            } else {
                eventsContainer.visibility = View.GONE
                noEventsText.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            println("DEBUG: Even fallback display failed: ${e.message}")
        }
    }

    private fun updateProgress(events: List<Event>) {
        try {
            val progress = if (events.isEmpty()) 0 else (events.count { it.isCompleted } * 100 / events.size)
            findViewById<ProgressBar>(R.id.circularProgressBar).progress = progress
            findViewById<TextView>(R.id.progressText).text = "$progress%"
        } catch (e: Exception) {
            println("DEBUG: Error updating progress: ${e.message}")
        }
    }

    private fun updateDateLabel() {
        try {
            val todayFormatter = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            val displayDate = currentDisplayedDate?.let { dateKey ->
                try {
                    val parts = dateKey.split("-")
                    val cal = Calendar.getInstance()
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    cal.time
                } catch (e: Exception) {
                    println("DEBUG: Error parsing date: $dateKey")
                    calendar.time
                }
            } ?: calendar.time

            todaySummaryLabel.text = if (isSelectedDateToday()) "Today's Summary"
            else "${todayFormatter.format(displayDate)} Summary"
        } catch (e: Exception) {
            println("DEBUG: Error updating date label: ${e.message}")
        }
    }

    private fun updateTasksSummary(tasks: List<TaskActivity.Task>) {
        try {
            val completedCount = tasks.count { it.isCompleted }
            val inProgressCount = tasks.count { !it.isCompleted }
            val upcomingCount = 0

            findViewById<TextView>(R.id.completedCountText).text = completedCount.toString()
            findViewById<TextView>(R.id.inProgressCountText).text = inProgressCount.toString()
            findViewById<TextView>(R.id.upcomingCountText).text = upcomingCount.toString()
        } catch (e: Exception) {
            println("DEBUG: Error updating tasks summary: ${e.message}")
        }
    }

    private fun isSelectedDateToday(): Boolean {
        val today = Calendar.getInstance()
        val todayFormatted = dateFormatter.format(today.time)
        return currentDisplayedDate == todayFormatted
    }

    private fun convertTaskDateToCalendarFormat(taskDate: String): String? {
        return try {
            println("DEBUG: Converting task date: '$taskDate'")

            // Handle both possible formats: "dd/MM/yyyy" and "yyyy-MM-dd"
            if (taskDate.contains("/")) {
                // Convert from "dd/MM/yyyy" to "yyyy-MM-dd"
                val parts = taskDate.split("/")
                if (parts.size == 3) {
                    val day = parts[0].padStart(2, '0')
                    val month = parts[1].padStart(2, '0')
                    val year = parts[2]
                    val converted = "$year-$month-$day"
                    println("DEBUG: Converted '$taskDate' to: '$converted'")
                    return converted
                }
            } else if (taskDate.contains("-")) {
                // Already in correct format
                println("DEBUG: Date already in correct format: '$taskDate'")
                return taskDate
            }

            println("DEBUG: Invalid date format: '$taskDate'")
            null
        } catch (e: Exception) {
            println("DEBUG: Error converting date '$taskDate': ${e.message}")
            null
        }
    }

    private fun openTaskDetails(title: String, location: String, time: String) {
        val intent = Intent(this, TaskActivity::class.java).apply {
            putExtra("TASK_TITLE", title)
            putExtra("TASK_LOCATION", location)
            putExtra("TASK_TIME", time)
        }
        startActivity(intent)
    }

    private fun startTimer(minutes: Int) {
        val intent = Intent(this, studytimer::class.java).apply {
            putExtra("TIMER_MINUTES", minutes)
        }
        startActivity(intent)
    }

    private fun showCustomTimerDialog() {
        Toast.makeText(this, "Custom timer selected", Toast.LENGTH_SHORT).show()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    data class Event(
        val title: String,
        val location: String,
        val time: String,
        val color: String,
        val isCompleted: Boolean = false
    )
}