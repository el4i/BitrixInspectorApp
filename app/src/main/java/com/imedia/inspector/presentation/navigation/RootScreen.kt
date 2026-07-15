package com.imedia.inspector.presentation.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imedia.inspector.domain.model.AppScreenState
import com.imedia.inspector.presentation.screens.AuthScreen
import com.imedia.inspector.presentation.screens.InspectorScreen
import com.imedia.inspector.presentation.screens.NeedRegistrationScreen
import com.imedia.inspector.presentation.screens.PendingRegistrationScreen
import com.imedia.inspector.presentation.screens.WorkerScreen
import com.imedia.inspector.presentation.viewmodel.MainViewModel

@Composable
fun RootScreen(context: Context, viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val isAutoUpload by viewModel.isAutoUpload.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    event?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeEvent()
        }
    }

    when (val s = state) {
        AppScreenState.Loading -> LoadingBox()
        AppScreenState.Blocked -> BlockedScreen(onRetry = viewModel::refresh)
        AppScreenState.LoggedOut -> AuthScreen(
            onLogin = viewModel::login,
            onRegister = { id -> viewModel.goToRegistration(id) }
        )
        AppScreenState.NeedRegistration -> NeedRegistrationScreen(
            onRegisterClick = { f, l, e -> viewModel.register(f, l, e) },
            onRefresh = viewModel::refresh,
            onCancel = viewModel::cancelRegistration
        )
        AppScreenState.PendingRegistration -> PendingRegistrationScreen(
            onRefresh = viewModel::refresh,
            onCancel = viewModel::cancelRegistration
        )
        is AppScreenState.InspectorFlow -> InspectorScreen(
            context = context,
            addresses = s.addresses,
            selected = s.selected,
            hasSkipped = s.hasSkipped,
            mode = s.mode,
            selectedTab = s.selectedTab,
            onTabSelect = viewModel::setTab,
            onSelect = viewModel::selectAddress,
            onDeselect = viewModel::deselectAddress,
            onLoadAddresses = { viewModel.refresh() },
            onLoadSkipped = { viewModel.loadInspectorAddress(skipped = true) },
            onOpenSkipChooser = viewModel::openBreakageChooser,
            onElevatorBroken = { reason -> viewModel.skipAddressElevatorBroken(reason) },
            onSendToRepair = viewModel::sendStandToRepair,
            onDismissSkipChooser = viewModel::closeBreakageChooser,
            onPhotoTaken = viewModel::uploadInspectorPhoto,
            onLogout = viewModel::logout,
            onManualSync = viewModel::manualSync,
            isAutoUpload = isAutoUpload,
            onToggleAutoUpload = viewModel::toggleAutoUpload,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::setSearchQuery
        )
        is AppScreenState.WorkerFlow -> WorkerScreen(
            context = context,
            addresses = s.addresses,
            selected = s.selected,
            hasSkipped = s.hasSkipped,
            mode = s.mode,
            selectedTab = s.selectedTab,
            onTabSelect = viewModel::setTab,
            onSelect = viewModel::selectAddress,
            onDeselect = viewModel::deselectAddress,
            onLoadAddresses = { viewModel.refresh() },
            onLoadSkipped = { viewModel.loadWorkerAddress(skipped = true) },
            onSkipAddress = viewModel::skipWorkerAddress,
            onPhotoTaken = viewModel::uploadWorkerPhoto,
            onLogout = viewModel::logout,
            onManualSync = viewModel::manualSync,
            isAutoUpload = isAutoUpload,
            onToggleAutoUpload = viewModel::toggleAutoUpload,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::setSearchQuery
        )
        is AppScreenState.Error -> ErrorBox(
            message = s.message, 
            onRetry = viewModel::refresh,
            onBack = viewModel::cancelRegistration
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BlockedScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Приложение временно не отвечает",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Обновить")
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ошибка: $message", color = androidx.compose.ui.graphics.Color.Red)
            androidx.compose.material3.Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("Повторить")
            }
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("Вернуться к логину")
            }
        }
    }
}
