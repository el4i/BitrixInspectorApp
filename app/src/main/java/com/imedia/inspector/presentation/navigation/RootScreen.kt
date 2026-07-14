package com.imedia.inspector.presentation.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.imedia.inspector.domain.model.AppScreenState
import com.imedia.inspector.presentation.screens.InspectorScreen
import com.imedia.inspector.presentation.screens.NeedRegistrationScreen
import com.imedia.inspector.presentation.screens.PendingRegistrationScreen
import com.imedia.inspector.presentation.screens.WorkerScreen
import com.imedia.inspector.presentation.viewmodel.MainViewModel

@Composable
fun RootScreen(context: Context, viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()

    event?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeEvent()
        }
    }

    when (val s = state) {
        AppScreenState.Loading -> LoadingBox()
        AppScreenState.NeedRegistration -> NeedRegistrationScreen(
            onRegisterClick = viewModel::register,
            onRefresh = viewModel::refresh
        )
        AppScreenState.PendingRegistration -> PendingRegistrationScreen(onRefresh = viewModel::refresh)
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
            onLoadAddresses = { viewModel.loadInspectorAddress(skipped = false) },
            onLoadSkipped = { viewModel.loadInspectorAddress(skipped = true) },
            onOpenSkipChooser = viewModel::openBreakageChooser,
            onElevatorBroken = viewModel::skipAddressElevatorBroken,
            onSendToRepair = viewModel::sendStandToRepair,
            onDismissSkipChooser = viewModel::closeBreakageChooser,
            onPhotoTaken = viewModel::uploadInspectorPhoto
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
            onLoadAddresses = { viewModel.loadWorkerAddress(skipped = false) },
            onLoadSkipped = { viewModel.loadWorkerAddress(skipped = true) },
            onSkipAddress = viewModel::skipWorkerAddress,
            onPhotoTaken = viewModel::uploadWorkerPhoto
        )
        is AppScreenState.Error -> ErrorBox(message = s.message, onRetry = viewModel::refresh)
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Ошибка: $message")
    }
}
