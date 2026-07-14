package com.imedia.inspector.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Аналог меню [["Регистрация"]] и ответа "Заявка на регистрацию принята." */
@Composable
fun NeedRegistrationScreen(
    onRegisterClick: (String, String, String) -> Unit, 
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    val isFormValid = firstName.isNotBlank() && lastName.isNotBlank() && email.contains("@")
    
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
                "Заполните анкету для регистрации",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Имя") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Фамилия") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                placeholder = { Text("example@mail.com") }
            )
            
            Button(
                onClick = { if (isFormValid) onRegisterClick(firstName, lastName, email) },
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Регистрация")
            }
            TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 8.dp)) {
                Text("Обновить статус")
            }
            TextButton(onClick = onCancel) {
                Text("Войти под другим ID", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Аналог ответа "Ожидайте, Ваша заявка находится на рассмотрении" */
@Composable
fun PendingRegistrationScreen(onRefresh: () -> Unit, onCancel: () -> Unit) {
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
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить статус")
            }
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("Изменить ID / Назад")
            }
        }
    }
}
