package com.imedia.inspector.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Аналог меню [["Регистрация"]] и ответа "Заявка на регистрацию принята." */
@Composable
fun NeedRegistrationScreen(onRegisterClick: () -> Unit, onRefresh: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
            Text("Доступ ограничен", style = MaterialTheme.typography.titleLarge)
            Text(
                "Оставьте заявку на регистрацию, чтобы начать работу",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = onRegisterClick) {
                Text("Регистрация")
            }
            androidx.compose.material3.TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 8.dp)) {
                Text("Обновить статус")
            }
        }
    }
}

/** Аналог ответа "Ожидайте, Ваша заявка находится на рассмотрении" */
@Composable
fun PendingRegistrationScreen(onRefresh: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
            Text("Заявка на рассмотрении", style = MaterialTheme.typography.titleLarge)
            Text(
                "Ожидайте, Ваша заявка находится на рассмотрении",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = onRefresh) {
                Text("Проверить статус")
            }
        }
    }
}
