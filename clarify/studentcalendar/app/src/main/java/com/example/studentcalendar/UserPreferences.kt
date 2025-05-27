package com.example.studentcalendar

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PASSWORD = stringPreferencesKey("user_password")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        // Remove the global TASKS_LIST and use user-specific keys instead
    }

    private val gson = Gson()

    // Generate a user-specific key for tasks
    private fun getTasksKeyForUser(email: String): Preferences.Key<String> {
        return stringPreferencesKey("tasks_list_${email.hashCode()}")
    }

    suspend fun saveName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

    suspend fun saveCredentials(email: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_EMAIL] = email
            preferences[USER_PASSWORD] = password
        }
    }

    suspend fun setLoggedIn(isLoggedIn: Boolean, rememberMe: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = isLoggedIn
            preferences[REMEMBER_ME] = rememberMe
        }
    }

    suspend fun saveLoginData(name: String, email: String, password: String, rememberMe: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
            preferences[USER_EMAIL] = email
            preferences[USER_PASSWORD] = password
            preferences[IS_LOGGED_IN] = true
            preferences[REMEMBER_ME] = rememberMe
        }
    }

    suspend fun clearLoginData() {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = false
            if (!(preferences[REMEMBER_ME] ?: false)) {
                preferences.remove(USER_NAME)
                preferences.remove(USER_EMAIL)
                preferences.remove(USER_PASSWORD)
            }
            preferences[REMEMBER_ME] = false
        }
    }

    suspend fun saveTasks(tasks: List<TaskActivity.Task>, userEmail: String) {
        try {
            val json = gson.toJson(tasks)
            val tasksKey = getTasksKeyForUser(userEmail)
            context.dataStore.edit { preferences ->
                preferences[tasksKey] = json
            }
            println("DEBUG: Tasks saved successfully for user: $userEmail, count: ${tasks.size}")
        } catch (e: Exception) {
            println("DEBUG: Error saving tasks: ${e.message}")
            throw e
        }
    }

    fun getTasksFlow(userEmail: String): Flow<List<TaskActivity.Task>> {
        val tasksKey = getTasksKeyForUser(userEmail)
        return context.dataStore.data.map { preferences ->
            val json = preferences[tasksKey] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<TaskActivity.Task>>() {}.type
                val tasks = gson.fromJson<List<TaskActivity.Task>>(json, type) ?: emptyList()
                println("DEBUG: Tasks loaded from preferences for user: $userEmail, count: ${tasks.size}")
                tasks
            } catch (e: Exception) {
                println("DEBUG: Error loading tasks: ${e.message}")
                emptyList()
            }
        }
    }

    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL] }
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_LOGGED_IN] ?: false }
    val rememberMeFlow: Flow<Boolean> = context.dataStore.data.map { it[REMEMBER_ME] ?: false }
}