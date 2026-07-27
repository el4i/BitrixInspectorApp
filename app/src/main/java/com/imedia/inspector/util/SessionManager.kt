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

    fun isAccessibleModeEnabled(): Boolean {
        return prefs.getBoolean("accessible_mode", false)
    }

    fun setAccessibleModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("accessible_mode", enabled).apply()
    }

    // --- Поля для восстановления после вылета (Low RAM) ---
    fun savePendingPhotoTask(addressId: String, filePath: String, isWorker: Boolean) {
        prefs.edit()
            .putString("pending_addr_id", addressId)
            .putString("pending_file_path", filePath)
            .putBoolean("pending_is_worker", isWorker)
            .apply()
    }

    fun getPendingPhotoAddrId(): String? = prefs.getString("pending_addr_id", null)
    fun getPendingPhotoPath(): String? = prefs.getString("pending_file_path", null)
    fun isPendingPhotoWorker(): Boolean = prefs.getBoolean("pending_is_worker", false)

    fun clearPendingPhotoTask() {
        prefs.edit()
            .remove("pending_addr_id")
            .remove("pending_file_path")
            .remove("pending_is_worker")
            .apply()
    }

    fun isLoggedIn(): Boolean = getUserId() != null
}
