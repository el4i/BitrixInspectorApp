package com.imedia.inspector.presentation.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imedia.inspector.domain.model.AddressItem
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.WorkerMode
import com.imedia.inspector.presentation.components.CameraCaptureButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScreen(
    context: Context,
    addresses: List<AddressItem>,
    selected: AddressItem?,
    hasSkipped: Boolean,
    mode: WorkerMode,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onSelect: (AddressItem) -> Unit,
    onDeselect: () -> Unit,
    onLoadAddresses: () -> Unit,
    onLoadSkipped: () -> Unit,
    onSkipAddress: () -> Unit,
    onPhotoTaken: (File) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Ремонт" else "Детали задания") },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = onDeselect) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (selected == null) {
                        IconButton(onClick = onLoadAddresses) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selected == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelect(0) },
                        text = { Text("Новые") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelect(1) },
                        text = { Text("Пропущенные") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { onTabSelect(2) },
                        text = { Text("Загруженные") }
                    )
                }

                val filteredList = when (selectedTab) {
                    0 -> addresses.filter { 
                        it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.SENT_TO_REPAIR 
                    }
                    1 -> addresses.filter { 
                        it.status == AddressStatus.SKIPPED_WORKER 
                    }
                    2 -> addresses.filter { 
                        !it.localPhotoPath.isNullOrBlank() || it.status == AddressStatus.REPAIR_DONE
                    }
                    else -> emptyList()
                }

                WorkerListContent(
                    padding = PaddingValues(0.dp),
                    addresses = filteredList,
                    onSelect = onSelect,
                    onLoadAddresses = onLoadAddresses
                )
            }
        } else {
            BackHandler(onBack = onDeselect)
            WorkerDetailContent(
                padding = padding,
                context = context,
                address = selected,
                onSkipAddress = onSkipAddress,
                onPhotoTaken = onPhotoTaken
            )
        }
    }
}

@Composable
private fun WorkerListContent(
    padding: PaddingValues,
    addresses: List<AddressItem>,
    onSelect: (AddressItem) -> Unit,
    onLoadAddresses: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (addresses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет заданий на ремонт", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onLoadAddresses) { Text("Обновить") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(addresses) { item ->
                    WorkerAddressCard(item = item, onClick = { onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun WorkerAddressCard(item: AddressItem, onClick: () -> Unit) {
    val isPending = !item.localPhotoPath.isNullOrBlank()
    val isUploaded = item.status == AddressStatus.REPAIR_DONE && !isPending

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = when {
            isUploaded -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            isPending -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            else -> CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isUploaded -> Icons.Default.CheckCircle
                    isPending -> Icons.Default.CloudUpload
                    else -> Icons.Default.Build
                },
                contentDescription = null,
                tint = when {
                    isUploaded -> MaterialTheme.colorScheme.primary
                    isPending -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline
                }
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = item.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isUploaded -> MaterialTheme.colorScheme.onPrimaryContainer
                        isPending -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                when {
                    isUploaded -> {
                        Text("Ремонт подтвержден (в Битриксе)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    isPending -> {
                        Text("Ожидает интернета", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    else -> item.breakageReason?.let {
                        Text("Поломка: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerDetailContent(
    padding: PaddingValues,
    context: Context,
    address: AddressItem,
    onSkipAddress: () -> Unit,
    onPhotoTaken: (File) -> Unit
) {
    val isDone = !address.localPhotoPath.isNullOrBlank() || 
                 address.status == AddressStatus.REPAIR_DONE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
    ) {
        Text("Ремонт по адресу", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(address.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        
        Spacer(Modifier.height(16.dp))

        if (isDone) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    val statusText = if (!address.localPhotoPath.isNullOrBlank()) 
                        "Фото ремонта ожидает интернета" 
                    else 
                        "Фото ремонта отправлено на сервер"

                    Text(statusText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    
                    if (!address.localPhotoPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(address.localPhotoPath),
                            contentDescription = "Сохраненное фото",
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            address.breakageReason?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Что нужно сделать: $it",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            CameraCaptureButton(
                context = context,
                label = "Сфотографировать готовый ремонт",
                onPhotoTaken = onPhotoTaken
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onSkipAddress,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Пропустить адрес") }
        }
    }
}
