package com.imedia.inspector.presentation.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.BreakageReason
import com.imedia.inspector.domain.model.InspectorMode
import com.imedia.inspector.presentation.components.CameraCaptureButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen(
    context: Context,
    addresses: List<AddressItem>,
    selected: AddressItem?,
    hasSkipped: Boolean,
    mode: InspectorMode,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onSelect: (AddressItem) -> Unit,
    onDeselect: () -> Unit,
    onLoadAddresses: () -> Unit,
    onLoadSkipped: () -> Unit,
    onOpenSkipChooser: () -> Unit,
    onElevatorBroken: () -> Unit,
    onSendToRepair: (BreakageReason) -> Unit,
    onDismissSkipChooser: () -> Unit,
    onPhotoTaken: (File) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (selected == null) "Монтажник" else "Детали адреса")
                },
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
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Выйти")
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
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { onTabSelect(3) },
                        text = { Text("На ремонте") }
                    )
                }
                
                // Фильтрация списка на основе вкладки
                val filteredList = when (selectedTab) {
                    0 -> addresses.filter { 
                        it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.NEW 
                    }
                    1 -> addresses.filter { 
                        it.status == AddressStatus.SKIPPED_INSPECTOR 
                    }
                    2 -> addresses.filter { 
                        it.status == AddressStatus.PHOTO_UPLOADED || !it.localPhotoPath.isNullOrBlank()
                    }
                    3 -> addresses.filter { 
                        it.status == AddressStatus.SENT_TO_REPAIR || it.status == AddressStatus.REPAIR_DONE
                    }
                    else -> emptyList()
                }

                AddressListContent(
                    padding = PaddingValues(0.dp),
                    addresses = filteredList,
                    onSelect = onSelect,
                    onLoadAddresses = onLoadAddresses
                )
            }
        } else {
            BackHandler(onBack = onDeselect)
            AddressDetailContent(
                padding = padding,
                context = context,
                address = selected,
                onOpenSkipChooser = onOpenSkipChooser,
                onPhotoTaken = onPhotoTaken
            )
        }

        if (mode == InspectorMode.CHOOSING_BREAKAGE) {
            ModalBottomSheet(onDismissRequest = onDismissSkipChooser) {
                BreakageChooserContent(
                    onElevatorBroken = onElevatorBroken,
                    onSendToRepair = onSendToRepair
                )
            }
        }
    }
}

@Composable
private fun AddressListContent(
    padding: PaddingValues,
    addresses: List<AddressItem>,
    onSelect: (AddressItem) -> Unit,
    onLoadAddresses: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (addresses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Список пуст", style = MaterialTheme.typography.headlineSmall)
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
                    AddressItemCard(item = item, onClick = { onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun AddressItemCard(item: AddressItem, onClick: () -> Unit) {
    val isPending = !item.localPhotoPath.isNullOrBlank()
    val isUploaded = (item.status == AddressStatus.PHOTO_UPLOADED || item.status == AddressStatus.SENT_TO_REPAIR) && !isPending
    
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
                    else -> Icons.Default.LocationOn
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
                        Text("Отправлено на сервер", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    isPending -> {
                        Text("Ожидает интернета", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    !item.property107.isNullOrBlank() -> {
                        Text("№ ${item.property107}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressDetailContent(
    padding: PaddingValues,
    context: Context,
    address: AddressItem,
    onOpenSkipChooser: () -> Unit,
    onPhotoTaken: (File) -> Unit
) {
    val isDone = !address.localPhotoPath.isNullOrBlank() || 
                 address.status == AddressStatus.PHOTO_UPLOADED || 
                 address.status == AddressStatus.SENT_TO_REPAIR
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
    ) {
        Text("Адрес объекта", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(address.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        
        Spacer(Modifier.height(24.dp))

        if (isDone) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(16.dp)) {
                    val statusText = if (!address.localPhotoPath.isNullOrBlank()) 
                        "Фото сохранено и ожидает интернета" 
                    else 
                        "Фото успешно отправлено на сервер"
                    
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
        } else {
            Spacer(Modifier.height(16.dp))
            
            CameraCaptureButton(
                context = context,
                label = "Сфотографировать и отправить",
                onPhotoTaken = onPhotoTaken
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onOpenSkipChooser,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Пропустить / Проблема") }
        }
    }
}

@Composable
private fun BreakageChooserContent(
    onElevatorBroken: () -> Unit,
    onSendToRepair: (BreakageReason) -> Unit
) {
    var showStandOptions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
        Text("В чем проблема?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (!showStandOptions) {
            Button(
                onClick = onElevatorBroken,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) { Text("Лифт не работает") }
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { showStandOptions = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Стенд сломан") }
        } else {
            BreakageReason.entries.forEach { reason ->
                TextButton(onClick = { onSendToRepair(reason) }, modifier = Modifier.fillMaxWidth()) {
                    Text(reason.label)
                }
                HorizontalDivider()
            }
        }
    }
}
