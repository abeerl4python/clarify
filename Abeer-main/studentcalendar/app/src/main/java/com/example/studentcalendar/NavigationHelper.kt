package com.example.studentcalendar

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavigationHelper {

    fun setupBottomNavigation(activity: Activity, bottomNavigation: BottomNavigationView, currentItemId: Int) {
        // Set the current selected item
        bottomNavigation.selectedItemId = currentItemId

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (currentItemId != R.id.nav_home) {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_tasks -> {
                    if (currentItemId != R.id.nav_tasks) {
                        val intent = Intent(activity, TaskActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_timer -> {
                    if (currentItemId != R.id.nav_timer) {
                        val intent = Intent(activity, studytimer::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    }
                    true
                }
                R.id.nav_profile -> {
                    if (currentItemId != R.id.nav_profile) {
                        val intent = Intent(activity, SettingsActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    }
                    true
                }
                else -> false
            }
        }
    }
}