package com.imedia.inspector.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RemoteLogger {
    private val client = OkHttpClient()
    private var isEnabled = false
    private var userName = "Unknown"
    
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

    fun init(enabled: Boolean, name: String) {
        this.isEnabled = enabled
        this.userName = name
    }

    fun log(message: String) {
        if (!isEnabled) return
        
        val timestamp = dateFormat.format(Date())
        val fullMessage = "[$timestamp] $message"
        
        println("REMOTE_LOG: $fullMessage")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = FormBody.Builder()
                    .add("user", userName)
                    .add("log", fullMessage)
                    .build()

                val request = Request.Builder()
                    .url("https://reklama-lift-kazan.ru/bot/bx-logs.php")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
