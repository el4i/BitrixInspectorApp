package com.imedia.inspector.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imedia.inspector.data.repository.AddressUpdatePatch
import com.imedia.inspector.data.repository.BitrixRepository
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.AppScreenState
import com.imedia.inspector.domain.model.BreakageReason
import com.imedia.inspector.domain.model.ElevatorSkipReason
import com.imedia.inspector.domain.model.Contact
import com.imedia.inspector.domain.model.InspectorMode
import com.imedia.inspector.domain.model.UserRole
import com.imedia.inspector.domain.model.WorkerMode
import com.imedia.inspector.util.FileNameUtils
import com.imedia.inspector.util.SessionManager
import com.imedia.inspector.util.LocationClient
import com.imedia.inspector.di.AppModule
import com.imedia.inspector.domain.model.VersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest

class MainViewModel(
    private val repository: BitrixRepository,
    private val sessionManager: SessionManager,
    private val locationClient: LocationClient,
    private val currentVersionCode: Int,
    private val displayName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppScreenState>(AppScreenState.Loading)
    val uiState: StateFlow<AppScreenState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    private val _isAutoUpload = MutableStateFlow(sessionManager.isAutoUploadEnabled())
    val isAutoUpload: StateFlow<Boolean> = _isAutoUpload.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var contact: Contact? = null
    private var observeJob: Job? = null

    init {
        checkStatusAndStart()
    }

    private fun checkStatusAndStart() {
        viewModelScope.launch {
            _uiState.value = AppScreenState.Loading
            
            // 1. ПРОВЕРКА БЛОКИРОВКИ
            if (repository.checkIsBlocked()) {
                _uiState.value = AppScreenState.Blocked
                return@launch
            }

            // 2. ПРОВЕРКА ОБНОВЛЕНИЙ (только если не заблокировано)
            val update = try { repository.getLatestVersionInfo() } catch (e: Exception) { null }
            println("DEBUG_B24: Проверка версии. Текущая: $currentVersionCode, На сервере: ${update?.versionCode}")
            
            if (update != null && update.versionCode > currentVersionCode) {
                _uiState.value = AppScreenState.UpdateAvailable(update)
                return@launch
            }

            // 3. ОБЫЧНЫЙ СТАРТ
            val savedId = sessionManager.getUserId()
            if (savedId == null) {
                _uiState.value = AppScreenState.LoggedOut
            } else {
                refresh()
            }
        }
    }

    fun downloadAndInstall(info: VersionInfo, context: android.content.Context) {
        viewModelScope.launch {
            _events.value = "Скачивание обновления..."
            try {
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder().url(info.apkUrl).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) return@withContext null
                    
                    val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                    val apkFile = File(dir, "update.apk")
                    response.body?.byteStream()?.use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    apkFile
                }

                if (file != null && file.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    _events.value = "Ошибка скачивания файла"
                }
            } catch (e: Exception) {
                _events.value = "Ошибка при установке: ${e.message}"
            }
        }
    }

    fun login(userId: String) {
        if (userId.isBlank()) {
            _events.value = "Введите ID"
            return
        }
        viewModelScope.launch {
            _uiState.value = AppScreenState.Loading
            sessionManager.saveUserId(userId)
            refresh()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AppScreenState.Loading
            sessionManager.logout()
            contact = null
            observeJob?.cancel()
            _uiState.value = AppScreenState.LoggedOut
        }
    }

    fun cancelRegistration() {
        sessionManager.logout()
        _uiState.value = AppScreenState.LoggedOut
    }

    fun refresh() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = AppScreenState.Loading
            
            if (repository.checkIsBlocked()) {
                _uiState.value = AppScreenState.Blocked
                return@launch
            }

            try {
                val c = repository.getContact(userId)
                contact = c
                if (c.isRegistered) {
                    repository.saveContactToCache(userId, c)
                    repository.closeLeadIfExists(userId)
                    loadForRole(c)
                } else {
                    // Если пользователь не найден как контакт, проверяем, есть ли лид (заявка)
                    val leadId = repository.getLeadId(userId)
                    if (leadId > 0) {
                        _uiState.value = AppScreenState.PendingRegistration
                    } else {
                        // Если ни контакта, ни лида — выводим ошибку и остаемся на входе
                        _events.value = "Пользователь с ID $userId не найден. Пожалуйста, пройдите регистрацию."
                        sessionManager.logout() // Сбрасываем неверный ID
                        _uiState.value = AppScreenState.LoggedOut
                    }
                }
            } catch (e: Exception) {
                val cached = repository.getCachedContact(userId)
                if (cached != null) {
                    contact = cached
                    loadForRole(cached)
                    _events.value = "Работаем в оффлайне"
                } else {
                    // Вместо экрана ошибки возвращаем на логин или NeedRegistration
                    _events.value = "Ошибка: ${e.message ?: "Нет интернета"}"
                    _uiState.value = if (previousState is AppScreenState.LoggedOut || previousState is AppScreenState.Loading) {
                         AppScreenState.LoggedOut
                    } else {
                         previousState
                    }
                }
            }
        }
    }

    private suspend fun checkRegistrationStatus() {
        val userId = sessionManager.getUserId() ?: return
        val leadId = repository.getLeadId(userId)
        _uiState.value = if (leadId == 0) {
            AppScreenState.NeedRegistration
        } else {
            AppScreenState.PendingRegistration
        }
    }

    fun goToRegistration(preferredId: String) {
        if (preferredId.isNotBlank()) {
            sessionManager.saveUserId(preferredId)
        }
        _uiState.value = AppScreenState.NeedRegistration
    }

    fun register(firstName: String, lastName: String, email: String) {
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = AppScreenState.Loading
            
            // Если ID не был введен ранее, используем временный уникальный
            var userId = sessionManager.getUserId()
            if (userId == null) {
                userId = "reg_" + java.util.UUID.randomUUID().toString().take(8)
                sessionManager.saveUserId(userId)
            }

            try {
                val existingLeadId = repository.getLeadId(userId)
                if (existingLeadId == 0) {
                    val newId = repository.addLead(userId, firstName, lastName, email)
                    if (newId > 0) {
                        // Переключаемся на новый ID, выданный Битриксом
                        sessionManager.saveUserId(newId.toString())
                        _events.value = "Заявка принята. Ваш новый ID: $newId. Код придет на почту $email"
                    }
                }
                checkRegistrationStatus()
            } catch (e: Exception) {
                _events.value = "Ошибка при регистрации: ${e.message}"
                _uiState.value = previousState
            }
        }
    }

    private fun startObserving(isWorker: Boolean) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeAddresses(isWorker).collect { list ->
                updateStateWithList(list)
            }
        }
    }

    private fun updateStateWithList(list: List<AddressItem>) {
        val query = _searchQuery.value.lowercase()
        val filteredList = if (query.isBlank()) {
            list
        } else {
            list.filter { 
                it.name.lowercase().contains(query) || 
                it.id.contains(query) ||
                it.property107?.contains(query) == true
            }
        }
        val sortedList = sortAddresses(filteredList)
        
        val current = _uiState.value
        if (current is AppScreenState.InspectorFlow) {
            val updatedSelected = sortedList.find { it.id == current.selected?.id }
            _uiState.value = current.copy(addresses = sortedList, selected = updatedSelected)
        } else if (current is AppScreenState.WorkerFlow) {
            val updatedSelected = sortedList.find { it.id == current.selected?.id }
            _uiState.value = current.copy(addresses = sortedList, selected = updatedSelected)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Пересчитываем текущее состояние при изменении поиска
        viewModelScope.launch {
            val c = contact ?: return@launch
            val isWorker = c.role == UserRole.WORKER
            // Мы не можем легко получить текущий список из Flow без коллектора, 
            // поэтому просто триггерим обновление через репозиторий (он закеширован)
            val list = if (isWorker) repository.getAllRepairAddresses(c.route, false) 
                       else repository.getAllAddresses(c.route, false)
            updateStateWithList(list)
        }
    }

    private fun sortAddresses(list: List<AddressItem>): List<AddressItem> {
        val userRoutes = contact?.route ?: emptyList()
        return list.sortedWith(compareBy<AddressItem> { item ->
            // 1. Сначала сортируем по индексу маршрута в списке пользователя
            val routeIdx = item.routeCodes.firstNotNullOfOrNull { r ->
                val idx = userRoutes.indexOf(r)
                if (idx != -1) idx else null
            } ?: Int.MAX_VALUE
            routeIdx
        }.thenBy { 
            // 2. Затем внутри маршрута сортируем по порядковому номеру (как число)
            it.property107?.toDoubleOrNull() ?: Double.MAX_VALUE 
        })
    }

    private suspend fun loadForRole(c: Contact) {
        startObserving(c.role == UserRole.WORKER)
        when (c.role) {
            UserRole.MONTAJNIK -> loadInspectorAddress(skipped = false)
            UserRole.WORKER -> loadWorkerAddress(skipped = false)
            UserRole.UNKNOWN -> {
                _events.value = "Роль пользователя не определена"
                _uiState.value = AppScreenState.LoggedOut
            }
        }
    }

    // ---------------------------------------------------------------------
    // Общая логика выбора адреса
    // ---------------------------------------------------------------------

    fun selectAddress(item: AddressItem) {
        val current = _uiState.value
        if (current is AppScreenState.InspectorFlow) {
            _uiState.value = current.copy(selected = item, mode = InspectorMode.AWAITING_PHOTO)
            viewModelScope.launch {
                try { repository.setState(contact?.id!!, 2) } catch (e: Exception) {}
            }
        } else if (current is AppScreenState.WorkerFlow) {
            _uiState.value = current.copy(selected = item, mode = WorkerMode.AWAITING_PHOTO)
            viewModelScope.launch {
                try { repository.setState(contact?.id!!, 12) } catch (e: Exception) {}
            }
        }
    }

    fun setTab(index: Int) {
        val current = _uiState.value
        if (current is AppScreenState.InspectorFlow) {
            _uiState.value = current.copy(selectedTab = index)
        } else if (current is AppScreenState.WorkerFlow) {
            _uiState.value = current.copy(selectedTab = index)
        }
    }

    fun deselectAddress() {
        val current = _uiState.value
        if (current is AppScreenState.InspectorFlow) {
            _uiState.value = current.copy(selected = null, mode = InspectorMode.AWAITING_PHOTO)
        } else if (current is AppScreenState.WorkerFlow) {
            _uiState.value = current.copy(selected = null, mode = WorkerMode.AWAITING_PHOTO)
        }
    }

    // ---------------------------------------------------------------------
    // Инспектор (225)
    // ---------------------------------------------------------------------

    fun loadInspectorAddress(skipped: Boolean) {
        val c = contact ?: return
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = AppScreenState.Loading
            try {
                // Загружаем все, что относится к текущему пользователю
                val list = sortAddresses(repository.getAllAddresses(c.route, false, forceRefresh = true))
                
                _uiState.value = AppScreenState.InspectorFlow(
                    addresses = list,
                    selected = null,
                    hasSkipped = list.any { it.status == AddressStatus.SKIPPED_INSPECTOR },
                    mode = InspectorMode.AWAITING_PHOTO,
                    selectedTab = 0
                )
                val next = list.find { it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.NEW }
                if (next != null) {
                    selectAddress(next)
                } else {
                    deselectAddress()
                }
            } catch (e: Exception) {
                _events.value = "Ошибка загрузки: ${e.message}"
                _uiState.value = previousState
            }
        }
    }

    fun openBreakageChooser() {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        _uiState.value = current.copy(mode = InspectorMode.CHOOSING_BREAKAGE)
    }

    fun closeBreakageChooser() {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        _uiState.value = current.copy(mode = InspectorMode.AWAITING_PHOTO)
    }

    fun skipAddressElevatorBroken(reason: ElevatorSkipReason) {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        val item = current.selected ?: return
        val c = contact ?: return
        viewModelScope.launch {
            try {
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.SKIPPED_INSPECTOR,
                    handledByContactId = c.id,
                    breakageReason = reason.label
                ))
                _events.value = "Адрес пропущен: ${reason.label}"
                loadInspectorAddress(skipped = false)
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun sendStandToRepair(reason: BreakageReason) {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        val item = current.selected ?: return
        val c = contact ?: return
        viewModelScope.launch {
            try {
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.SENT_TO_REPAIR,
                    handledByContactId = c.id,
                    breakageReason = reason.label
                ))
                _events.value = "Отправлен на ремонт"
                loadInspectorAddress(skipped = false)
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun uploadInspectorPhoto(photoFile: File) {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        val item = current.selected ?: return
        val c = contact ?: return
        viewModelScope.launch {
            try {
                println("DEBUG_B24: Запрос GPS координат...")
                val location = locationClient.getCurrentLocation()
                
                if (location == null) {
                    _events.value = "Включите GPS для записи координат!"
                    println("DEBUG_B24: GPS результат: null (выключен или нет сигнала)")
                } else {
                    println("DEBUG_B24: GPS результат: lat=${location.latitude}, lon=${location.longitude}")
                }
                
                val ext = FileNameUtils.extensionFromFile(photoFile)
                val fileName = FileNameUtils.buildFileName(item.routeCodes.firstOrNull().orEmpty(), item.name, ext)
                val base64 = FileNameUtils.fileToBase64(photoFile)
                
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.PHOTO_UPLOADED,
                    handledByContactId = c.id,
                    fileName = fileName,
                    fileBase64 = base64,
                    localFilePath = photoFile.absolutePath,
                    latitude = location?.latitude,
                    longitude = location?.longitude
                ))
                _events.value = "Фото сохранено."
                
                // МГНОВЕННОЕ ОБНОВЛЕНИЕ СПИСКА В UI
                val list = sortAddresses(repository.getAllAddresses(c.route, false, forceRefresh = false))
                val currentRole = _uiState.value
                if (currentRole is AppScreenState.InspectorFlow) {
                    _uiState.value = currentRole.copy(addresses = list)
                }

                // После сохранения всегда выходим в список
                deselectAddress()
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    // ---------------------------------------------------------------------
    // Ремонтник (227)
    // ---------------------------------------------------------------------

    fun loadWorkerAddress(skipped: Boolean) {
        val c = contact ?: return
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = AppScreenState.Loading
            try {
                val list = sortAddresses(repository.getAllRepairAddresses(c.route, false, forceRefresh = true))
                
                _uiState.value = AppScreenState.WorkerFlow(
                    addresses = list,
                    selected = null,
                    hasSkipped = list.any { it.status == AddressStatus.SKIPPED_WORKER },
                    mode = WorkerMode.AWAITING_PHOTO,
                    selectedTab = 0
                )
                val next = list.find { it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.SENT_TO_REPAIR }
                if (next != null) {
                    selectAddress(next)
                } else {
                    deselectAddress()
                }
            } catch (e: Exception) {
                _events.value = "Ошибка загрузки: ${e.message}"
                _uiState.value = previousState
            }
        }
    }

    fun skipWorkerAddress() {
        val current = (_uiState.value as? AppScreenState.WorkerFlow) ?: return
        val item = current.selected ?: return
        viewModelScope.launch {
            try {
                repository.updateAddress(item, AddressUpdatePatch(status = AddressStatus.SKIPPED_WORKER))
                _events.value = "Адрес пропущен."
                loadWorkerAddress(skipped = false)
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun uploadWorkerPhoto(photoFile: File) {
        val current = (_uiState.value as? AppScreenState.WorkerFlow) ?: return
        val item = current.selected ?: return
        val c = contact ?: return
        viewModelScope.launch {
            try {
                println("DEBUG_B24: Запрос GPS координат...")
                val location = locationClient.getCurrentLocation()
                
                if (location == null) {
                    _events.value = "Включите GPS для записи координат!"
                    println("DEBUG_B24: GPS результат: null (выключен или нет сигнала)")
                } else {
                    println("DEBUG_B24: GPS результат: lat=${location.latitude}, lon=${location.longitude}")
                }
                
                val ext = FileNameUtils.extensionFromFile(photoFile)
                val fileName = FileNameUtils.buildFileName(item.routeCodes.firstOrNull().orEmpty(), item.name, ext)
                val base64 = FileNameUtils.fileToBase64(photoFile)
                
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.REPAIR_DONE,
                    handledByContactId = c.id,
                    fileName = fileName,
                    fileBase64 = base64,
                    localFilePath = photoFile.absolutePath,
                    latitude = location?.latitude,
                    longitude = location?.longitude
                ))
                _events.value = "Фото сохранено."

                val list = sortAddresses(repository.getAllRepairAddresses(c.route, false, forceRefresh = false))
                val flow = _uiState.value as? AppScreenState.WorkerFlow
                if (flow != null) {
                    _uiState.value = flow.copy(addresses = list)
                }

                // После сохранения всегда выходим в список
                deselectAddress()
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun consumeEvent() { _events.value = null }
    fun reset() { refresh() }

    fun toggleAutoUpload(enabled: Boolean) {
        sessionManager.setAutoUploadEnabled(enabled)
        _isAutoUpload.value = enabled
        if (enabled) {
            AppModule.scheduleSync()
        }
    }

    fun manualSync() {
        _events.value = "Синхронизация запущена..."
        AppModule.scheduleSync(forceManual = true)
    }
}
