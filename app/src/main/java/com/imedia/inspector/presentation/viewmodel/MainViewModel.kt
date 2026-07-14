package com.imedia.inspector.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imedia.inspector.data.repository.AddressUpdatePatch
import com.imedia.inspector.data.repository.BitrixRepository
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.AppScreenState
import com.imedia.inspector.domain.model.BreakageReason
import com.imedia.inspector.domain.model.Contact
import com.imedia.inspector.domain.model.InspectorMode
import com.imedia.inspector.domain.model.UserRole
import com.imedia.inspector.domain.model.WorkerMode
import com.imedia.inspector.util.FileNameUtils
import com.imedia.inspector.util.SessionManager
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
    private val displayName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppScreenState>(AppScreenState.Loading)
    val uiState: StateFlow<AppScreenState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    private var contact: Contact? = null
    private var observeJob: Job? = null

    init {
        val savedId = sessionManager.getUserId()
        if (savedId == null) {
            _uiState.value = AppScreenState.LoggedOut
        } else {
            refresh()
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
            try {
                val c = repository.getContact(userId)
                contact = c
                if (c.isRegistered) {
                    repository.saveContactToCache(userId, c)
                    loadForRole(c)
                } else {
                    checkRegistrationStatus()
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

    fun register(firstName: String, lastName: String, email: String) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = AppScreenState.Loading
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
                val current = _uiState.value
                if (current is AppScreenState.InspectorFlow) {
                    val updatedSelected = list.find { it.id == current.selected?.id }
                    _uiState.value = current.copy(addresses = list, selected = updatedSelected)
                } else if (current is AppScreenState.WorkerFlow) {
                    val updatedSelected = list.find { it.id == current.selected?.id }
                    _uiState.value = current.copy(addresses = list, selected = updatedSelected)
                }
            }
        }
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
                val list = repository.getAllAddresses(c.route, false, forceRefresh = true)
                
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

    fun skipAddressElevatorBroken() {
        val current = (_uiState.value as? AppScreenState.InspectorFlow) ?: return
        val item = current.selected ?: return
        viewModelScope.launch {
            try {
                repository.updateAddress(item, AddressUpdatePatch(status = AddressStatus.SKIPPED_INSPECTOR))
                _events.value = "Адрес пропущен."
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
                val ext = FileNameUtils.extensionFromFile(photoFile)
                val fileName = FileNameUtils.buildFileName(item.routeCodes.firstOrNull().orEmpty(), item.name, ext)
                val base64 = FileNameUtils.fileToBase64(photoFile)
                
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.PHOTO_UPLOADED,
                    handledByContactId = c.id,
                    fileName = fileName,
                    fileBase64 = base64,
                    localFilePath = photoFile.absolutePath
                ))
                _events.value = "Фото сохранено."
                
                // МГНОВЕННОЕ ОБНОВЛЕНИЕ СПИСКА В UI
                val list = repository.getAllAddresses(c.route, false, forceRefresh = false)
                val currentRole = _uiState.value
                if (currentRole is AppScreenState.InspectorFlow) {
                    _uiState.value = currentRole.copy(addresses = list)
                }

                // Авто-переход к следующему
                val next = list.find { it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.NEW }

                if (next != null) {
                    selectAddress(next)
                } else {
                    deselectAddress()
                }
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
                val list = repository.getAllRepairAddresses(c.route, false, forceRefresh = true)
                
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
                val ext = FileNameUtils.extensionFromFile(photoFile)
                val fileName = FileNameUtils.buildFileName(item.routeCodes.firstOrNull().orEmpty(), item.name, ext)
                val base64 = FileNameUtils.fileToBase64(photoFile)
                
                repository.updateAddress(item, AddressUpdatePatch(
                    status = AddressStatus.REPAIR_DONE,
                    handledByContactId = c.id,
                    fileName = fileName,
                    fileBase64 = base64,
                    localFilePath = photoFile.absolutePath
                ))
                _events.value = "Фото сохранено."

                val list = repository.getAllRepairAddresses(c.route, false, forceRefresh = false)
                val flow = _uiState.value as? AppScreenState.WorkerFlow
                if (flow != null) {
                    _uiState.value = flow.copy(addresses = list)
                }

                // Авто-переход к следующему
                val next = list.filter { it.localPhotoPath.isNullOrBlank() && it.status == AddressStatus.SENT_TO_REPAIR }
                    .firstOrNull()

                if (next != null) {
                    selectAddress(next)
                } else {
                    deselectAddress()
                }
            } catch (e: Exception) {
                _events.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun consumeEvent() { _events.value = null }
    fun reset() { refresh() }
}
