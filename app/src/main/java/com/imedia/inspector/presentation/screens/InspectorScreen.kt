package com.imedia.inspector.presentation.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.BreakageReason
import com.imedia.inspector.domain.model.ElevatorSkipReason
import com.imedia.inspector.domain.model.InspectorMode
import com.imedia.inspector.presentation.components.CameraCaptureButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    onElevatorBroken: (ElevatorSkipReason) -> Unit,
    onSendToRepair: (BreakageReason) -> Unit,
    onDismissSkipChooser: () -> Unit,
    onPhotoTaken: (File) -> Unit,
    onLogout: () -> Unit,
    onManualSync: () -> Unit,
    isAutoUpload: Boolean,
    onToggleAutoUpload: (Boolean) -> Unit,
    isAccessibleMode: Boolean,
    onToggleAccessibleMode: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPreparePhoto: (String) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = selectedTab) { 4 }

    // Синхронизация внешнего selectedTab с пейджером
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    // Синхронизация пейджера с внешним состоянием (если нужно для MainViewModel)
    LaunchedEffect(pagerState.currentPage) {
        onTabSelect(pagerState.currentPage)
    }

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
                        
                        var showMenu by remember { mutableStateOf(false) }
                        var showAutoUploadConfirm by remember { mutableStateOf(false) }

                        if (showAutoUploadConfirm) {
                            AlertDialog(
                                onDismissRequest = { showAutoUploadConfirm = false },
                                title = { Text("Включить автозагрузку?") },
                                text = { Text("При включении этой функции фотографии будут отправляться в Битрикс автоматически сразу после съемки. Это может увеличить расход мобильного трафика.") },
                                confirmButton = {
                                    Button(onClick = {
                                        onToggleAutoUpload(true)
                                        showAutoUploadConfirm = false
                                    }) { Text("Включить") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAutoUploadConfirm = false }) { Text("Отмена") }
                                }
                            )
                        }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Автозагрузка")
                                        Spacer(Modifier.weight(1f))
                                        Switch(checked = isAutoUpload, onCheckedChange = { checked ->
                                            if (checked) {
                                                showAutoUploadConfirm = true
                                            } else {
                                                onToggleAutoUpload(false)
                                            }
                                            showMenu = false 
                                        })
                                    }
                                },
                                onClick = { }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Упрощенный режим")
                                        Spacer(Modifier.weight(1f))
                                        Switch(checked = isAccessibleMode, onCheckedChange = { checked ->
                                            onToggleAccessibleMode(checked)
                                            showMenu = false
                                        })
                                    }
                                },
                                onClick = { }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selected == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Поиск по названию или ID") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 16.dp) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { onTabSelect(0) },
                        text = { Text("Новые") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { onTabSelect(1) },
                        text = { Text("Пропущенные") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { onTabSelect(2) },
                        text = { Text("Загруженные") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick = { onTabSelect(3) },
                        text = { Text("На ремонте") }
                    )
                }
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val filteredList = when (page) {
                        0 -> addresses.filter { 
                            it.status == AddressStatus.NEW && !it.isPendingSync 
                        }
                        1 -> addresses.filter { 
                            it.status == AddressStatus.SKIPPED_INSPECTOR 
                        }
                        2 -> addresses.filter { 
                            it.status == AddressStatus.PHOTO_UPLOADED || (it.isPendingSync && !it.localPhotoPath.isNullOrBlank())
                        }
                        3 -> addresses.filter { 
                            it.status == AddressStatus.SENT_TO_REPAIR || it.status == AddressStatus.REPAIR_DONE
                        }
                        else -> emptyList()
                    }

                    AddressListContent(
                        padding = PaddingValues(0.dp),
                        addresses = filteredList,
                        selectedTab = page,
                        isAccessibleMode = isAccessibleMode,
                        onSelect = onSelect,
                        onLoadAddresses = onLoadAddresses,
                        onManualSync = onManualSync
                    )
                }
            }
        } else {
            BackHandler(onBack = onDeselect)
            AddressDetailContent(
                padding = padding,
                context = context,
                address = selected,
                isAccessibleMode = isAccessibleMode,
                onOpenSkipChooser = onOpenSkipChooser,
                onPrepare = { onPreparePhoto(selected.id) },
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
    selectedTab: Int,
    isAccessibleMode: Boolean,
    onSelect: (AddressItem) -> Unit,
    onLoadAddresses: () -> Unit,
    onManualSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (selectedTab == 2 && addresses.any { !it.localPhotoPath.isNullOrBlank() }) {
            Button(
                onClick = onManualSync,
                modifier = Modifier.fillMaxWidth().padding(if (isAccessibleMode) 24.dp else 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibleMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary),
                shape = if (isAccessibleMode) androidx.compose.foundation.shape.RoundedCornerShape(0.dp) else ButtonDefaults.shape
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = if (isAccessibleMode) Modifier.size(32.dp) else Modifier)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ВЫГРУЗИТЬ ФОТООТЧЕТ",
                    style = if (isAccessibleMode) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge
                )
            }
            HorizontalDivider(thickness = if (isAccessibleMode) 2.dp else 1.dp)
        }

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
                items(addresses.size) { index ->
                    val item = addresses[index]
                    AddressItemCard(
                        item = item, 
                        showPendingStatus = selectedTab == 2,
                        isAccessibleMode = isAccessibleMode,
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressItemCard(
    item: AddressItem, 
    showPendingStatus: Boolean = true,
    isAccessibleMode: Boolean = false,
    onClick: () -> Unit
) {
    // Синий статус "Ожидает" показываем ТОЛЬКО если есть фото и мы на вкладке загрузки.
    // Пропуски (скипы) теперь синхронизируются автоматом, им этот статус не нужен.
    val isPending = item.isPendingSync && showPendingStatus && !item.localPhotoPath.isNullOrBlank()
    val isUploaded = (item.status == AddressStatus.PHOTO_UPLOADED || item.status == AddressStatus.REPAIR_DONE) && !item.isPendingSync
    val isRepair = item.status == AddressStatus.SENT_TO_REPAIR && !item.isPendingSync
    val isSkipped = item.status == AddressStatus.SKIPPED_INSPECTOR

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isAccessibleMode) Modifier.border(2.dp, androidx.compose.ui.graphics.Color.Black, MaterialTheme.shapes.medium) else Modifier),
        colors = when {
            isAccessibleMode -> CardDefaults.elevatedCardColors(containerColor = androidx.compose.ui.graphics.Color.White)
            isRepair -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            isUploaded -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            isPending -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            else -> CardDefaults.elevatedCardColors()
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(if (isAccessibleMode) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isAccessibleMode) {
                Icon(
                    imageVector = when {
                        isRepair -> Icons.Default.Build
                        isUploaded -> Icons.Default.CheckCircle
                        isPending -> Icons.Default.CloudUpload
                        else -> Icons.Default.LocationOn
                    },
                    contentDescription = null,
                    tint = when {
                        isRepair -> MaterialTheme.colorScheme.error
                        isUploaded -> MaterialTheme.colorScheme.primary
                        isPending -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = item.name, 
                    style = if (isAccessibleMode) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else when {
                        isRepair -> MaterialTheme.colorScheme.onErrorContainer
                        isUploaded -> MaterialTheme.colorScheme.onPrimaryContainer
                        isPending -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isAccessibleMode) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Маршрут: ${item.routeCodes.firstOrNull() ?: ""}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                } else {
                    Text(
                        text = "Маршрут: ${item.routeCodes.firstOrNull() ?: ""} | № ${item.property107 ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                when {
                    isPending -> {
                        Text(
                            if (isAccessibleMode) "! ОЖИДАЕТ ОТПРАВКИ !" else "Ожидает интернета", 
                            style = if (isAccessibleMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, 
                            fontWeight = if (isAccessibleMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.secondary
                        )
                    }
                    isRepair -> {
                        val statusText = if (item.status == AddressStatus.REPAIR_DONE) "Ремонт выполнен" else "На ремонте"
                        Text(statusText, style = if (isAccessibleMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.error)
                    }
                    isUploaded -> {
                        Text(if (isAccessibleMode) "ЗАГРУЖЕНО" else "Отправлено на сервер", style = if (isAccessibleMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.primary)
                    }
                    isSkipped -> {
                        Text("${if (isAccessibleMode) "ПРОПУЩЕНО: " else "Адрес пропущен: "}${item.breakageReason ?: ""}", style = if (isAccessibleMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall, color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.outline)
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
    isAccessibleMode: Boolean,
    onOpenSkipChooser: () -> Unit,
    onPrepare: () -> Unit,
    onPhotoTaken: (File) -> Unit
) {
    // Адрес считается завершенным (кнопки скрыты), только если есть ФОТО или он в РЕМОНТЕ.
    // Пропущенные адреса (SKIPPED) позволяют переснять фото, поэтому кнопки для них НЕ скрываем.
    val isDone = (!address.localPhotoPath.isNullOrBlank() && address.isPendingSync) ||
                 address.status == AddressStatus.PHOTO_UPLOADED || 
                 address.status == AddressStatus.SENT_TO_REPAIR ||
                 address.status == AddressStatus.REPAIR_DONE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(if (isAccessibleMode) 16.dp else 24.dp)
            .then(if (isAccessibleMode) Modifier.background(androidx.compose.ui.graphics.Color.White) else Modifier)
    ) {
        Text(
            "Адрес объекта", 
            style = if (isAccessibleMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, 
            color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.secondary
        )
        Text(
            address.name, 
            style = if (isAccessibleMode) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, 
            fontWeight = FontWeight.ExtraBold,
            color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.height(24.dp))

        if (isDone) {
            Surface(
                color = if (isAccessibleMode) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                border = if (isAccessibleMode) androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Black) else null
            ) {
                Column(Modifier.padding(16.dp)) {
                    val statusText = if (address.isPendingSync) 
                        "Ожидает интернета для отправки" 
                    else 
                        "Успешно отправлено на сервер"
                    
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
                isAccessibleMode = isAccessibleMode,
                onPrepare = onPrepare,
                onPhotoTaken = onPhotoTaken
            )
            
            Spacer(Modifier.height(24.dp))
            
            OutlinedButton(
                onClick = onOpenSkipChooser,
                modifier = Modifier.fillMaxWidth().then(if (isAccessibleMode) Modifier.height(80.dp) else Modifier),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isAccessibleMode) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.error),
                border = if (isAccessibleMode) androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Black) else ButtonDefaults.outlinedButtonBorder
            ) { 
                Text(
                    text = "Пропустить / Проблема",
                    style = if (isAccessibleMode) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun BreakageChooserContent(
    onElevatorBroken: (ElevatorSkipReason) -> Unit,
    onSendToRepair: (BreakageReason) -> Unit
) {
    var showStandOptions by remember { mutableStateOf(false) }
    var showElevatorOptions by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
        val title = when {
            showStandOptions -> "Стенд сломан"
            showElevatorOptions -> "Проблема с лифтом"
            else -> "В чем проблема?"
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (!showStandOptions && !showElevatorOptions) {
            Button(
                onClick = { showElevatorOptions = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) { Text("Лифт не работает") }
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { showStandOptions = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Стенд сломан") }
        } else if (showElevatorOptions) {
            ElevatorSkipReason.entries.forEach { reason ->
                TextButton(onClick = { onElevatorBroken(reason) }, modifier = Modifier.fillMaxWidth()) {
                    Text(reason.label)
                }
                HorizontalDivider()
            }
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
