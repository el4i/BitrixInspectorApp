package com.imedia.inspector.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveUserId(userId: String) {
        prefs.edit().putString("device_user_id", userId).apply()
    }

    fun getUserId(): String? {
        return prefs.getString("device_user_id", null)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun isAutoUploadEnabled(): Boolean {
        return prefs.getBoolean("auto_upload", false) // По умолчанию ВЫКЛЮЧЕНО
    }

    fun setAutoUploadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_upload", enabled).apply()
    }

    fun isLoggedIn(): Boolean = getUserId() != null
}
