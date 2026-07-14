package com.imedia.inspector.data.repository

import com.imedia.inspector.data.local.AddressDao
import com.imedia.inspector.data.local.AddressEntity
import com.imedia.inspector.data.local.PendingUploadEntity
import com.imedia.inspector.data.local.UserConfigEntity
import com.imedia.inspector.data.local.Converters
import com.imedia.inspector.data.remote.Bitrix24ApiService
import com.imedia.inspector.data.remote.dto.ListElementDto
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.domain.model.AddressStatus
import com.imedia.inspector.domain.model.Contact
import com.imedia.inspector.domain.model.UserRole
import com.imedia.inspector.util.BitrixParamsBuilder
import java.io.File
import com.imedia.inspector.di.AppModule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BitrixRepository {
    suspend fun getContact(deviceUserId: String): Contact
    suspend fun setState(contactId: String, state: Int): Boolean
    suspend fun getAddressList(route: List<String>, skipped: Boolean): AddressItem?
    suspend fun getRepairList(route: List<String>, skipped: Boolean): AddressItem?
    
    // Методы наблюдения
    fun observeAddresses(isWorker: Boolean): Flow<List<AddressItem>>

    // Новые методы для получения списков
    suspend fun getAllAddresses(route: List<String>, skipped: Boolean, forceRefresh: Boolean = false): List<AddressItem>
    suspend fun getAllRepairAddresses(route: List<String>, skipped: Boolean, forceRefresh: Boolean = false): List<AddressItem>
    suspend fun updateAddress(item: AddressItem, patch: AddressUpdatePatch): Boolean
    suspend fun addLead(deviceUserId: String, firstName: String, lastName: String, email: String): Int
    suspend fun getLeadId(deviceUserId: String): Int

    // Новые методы для оффлайна
    suspend fun fetchAndCacheAddresses(route: List<String>, isWorker: Boolean)
    suspend fun uploadDirectly(item: AddressItem, patch: AddressUpdatePatch): Boolean

    suspend fun getCachedContact(deviceUserId: String): Contact?
    suspend fun saveContactToCache(deviceUserId: String, contact: Contact)
    suspend fun closeLeadIfExists(deviceUserId: String)
}

/** То, что реально меняется при updList() в разных ветках сценария. */
data class AddressUpdatePatch(
    val status: AddressStatus,
    val handledByContactId: String? = null,
    val breakageReason: String? = null,
    val fileName: String? = null,
    val fileBase64: String? = null,
    val localFilePath: String? = null // Для оффлайн режима
)

const val IBLOCK_ID_LISTS = "29"
const val IBLOCK_TYPE_LISTS = "lists"

class BitrixRepositoryImpl(
    private val api: Bitrix24ApiService,
    private val dao: AddressDao
) : BitrixRepository {

    private val addressMutexes = ConcurrentHashMap<String, Mutex>()

    private fun getMutex(addressId: String): Mutex {
        return addressMutexes.computeIfAbsent(addressId) { Mutex() }
    }


    override suspend fun getContact(deviceUserId: String): Contact {
        val params = BitrixParamsBuilder.build(
            mapOf(
                "FILTER" to mapOf("UF_CRM_1784014618374" to deviceUserId),
                "SELECT" to listOf(
                    "ID",
                    "UF_CRM_1784014618374",
                    "UF_CRM_1659870645432",
                    "UF_CRM_1659879474690",
                    "UF_CRM_1662016534407"
                )
            )
        )
        val contactDto = api.contactList(params).result?.firstOrNull()
        val roleCode = contactDto?.position.toB24String()
        println("DEBUG_B24: Загружена роль: $roleCode")

        return Contact(
            id = contactDto?.id,
            state = contactDto?.state.toB24String(),
            route = contactDto?.route.toB24List(),
            role = UserRole.fromCode(roleCode)
        )
    }

    override suspend fun setState(contactId: String, state: Int): Boolean {
        val params = BitrixParamsBuilder.build(
            mapOf(
                "ID" to contactId,
                "FIELDS" to mapOf("UF_CRM_1659870645432" to state),
                "PARAMS" to mapOf("REGISTER_SONET_EVENT" to "Y")
            )
        )
        return api.contactUpdate(params).result == true
    }

    override suspend fun getAddressList(route: List<String>, skipped: Boolean): AddressItem? {
        val status = if (skipped) AddressStatus.SKIPPED_INSPECTOR else AddressStatus.NEW
        // Сначала ищем в локальной базе
        val cached = dao.getNextAddress(isWorker = false, status = status)
        if (cached != null) return cached.toDomain()

        // Если в базе пусто, пробуем загрузить из сети (или возвращаем null)
        return fetchListElement(route, status.code)
    }

    override suspend fun getRepairList(route: List<String>, skipped: Boolean): AddressItem? {
        val status = if (skipped) AddressStatus.SKIPPED_WORKER else AddressStatus.SENT_TO_REPAIR
        val cached = dao.getNextAddress(isWorker = true, status = status)
        if (cached != null) return cached.toDomain()

        return fetchListElement(route, status.code)
    }

    override fun observeAddresses(isWorker: Boolean): Flow<List<AddressItem>> {
        return dao.observeAllForRole(isWorker).map { list -> 
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllAddresses(route: List<String>, skipped: Boolean, forceRefresh: Boolean): List<AddressItem> {
        if (forceRefresh) {
            fetchAndCacheAddresses(route, false)
        }
        val all = dao.getAllForRole(isWorker = false)
        return all.map { it.toDomain() }
    }

    override suspend fun getAllRepairAddresses(route: List<String>, skipped: Boolean, forceRefresh: Boolean): List<AddressItem> {
        if (forceRefresh) {
            fetchAndCacheAddresses(route, true)
        }
        val all = dao.getAllForRole(isWorker = true)
        return all.map { it.toDomain() }
    }

    private suspend fun fetchListElement(route: List<String>, status: String): AddressItem? {
        try {
            val params = BitrixParamsBuilder.build(
                mapOf(
                    "IBLOCK_TYPE_ID" to IBLOCK_TYPE_LISTS,
                    "IBLOCK_ID" to IBLOCK_ID_LISTS,
                    "ELEMENT_ORDER" to mapOf("PROPERTY_109" to "ASC", "PROPERTY_107" to "ASC"),
                    "FILTER" to mapOf(
                        "=PROPERTY_111" to route,
                        "=PROPERTY_109" to listOf(status)
                    )
                )
            )
            val dto = api.listElementGet(params).result?.firstOrNull() ?: return null
            return dto.toDomain()
        } catch (e: Exception) {
            return null // В оффлайне просто возвращаем null вместо ошибки
        }
    }

    override suspend fun updateAddress(item: AddressItem, patch: AddressUpdatePatch): Boolean {
        return getMutex(item.id).withLock {
            println("DEBUG_B24: Попытка обновления адреса ${item.id} (${item.name}). Статус: ${patch.status}")
            
            // Проверяем, нет ли уже в очереди загрузки для этого адреса
            // чтобы избежать дубликатов (но разрешаем обновление, если это тот же статус и путь)
            val existing = dao.getAllPendingUploads().find { it.addressId == item.id }
            if (existing != null) {
                if (existing.status == patch.status && existing.photoFilePath == patch.localFilePath) {
                    println("DEBUG_B24: Адрес ${item.id} уже находится в очереди с таким же статусом и фото. Пропуск.")
                    return@withLock true
                } else {
                    println("DEBUG_B24: В очереди уже есть задача для ${item.id}, но с другими данными. Удаляем старую.")
                    dao.deletePendingUpload(existing.localId)
                }
            }

            // Локально сразу помечаем адрес как выполненный и СОХРАНЯЕМ ПУТЬ К ФОТО
            dao.updateAddressStatus(item.id, patch.status, patch.localFilePath)

            // Сначала сохраняем данные в оффлайн-очередь в любом случае
            val pendingId = dao.insertPendingUpload(
                PendingUploadEntity(
                    addressId = item.id,
                    addressName = item.name,
                    routeJson = Converters().fromStringList(item.routeCodes), // СОХРАНЯЕМ МАРШРУТ
                    status = patch.status,
                    handledByContactId = patch.handledByContactId,
                    breakageReason = patch.breakageReason,
                    fileName = patch.fileName,
                    photoFilePath = patch.localFilePath,
                    property107 = item.property107
                )
            )

            try {
                // Пробуем отправить сразу (вызываем внутренний метод без лока, так как мы уже под локом)
                val success = uploadDirectlyInternal(item, patch)
                if (success) {
                    // Если отправилось - удаляем из очереди
                    dao.deletePendingUpload(pendingId)
                    return@withLock true
                }
            } catch (e: Exception) {
                println("DEBUG_B24: Ошибка при немедленной отправке (но сохранено в очередь): ${e.message}")
            }

            AppModule.scheduleSync()
            true // Всегда возвращаем true, чтобы UI переключился дальше
        }
    }

    override suspend fun uploadDirectly(item: AddressItem, patch: AddressUpdatePatch): Boolean {
        return getMutex(item.id).withLock {
            uploadDirectlyInternal(item, patch)
        }
    }

    private suspend fun uploadDirectlyInternal(item: AddressItem, patch: AddressUpdatePatch): Boolean {
        // ЕСЛИ ЕСТЬ НОВОЕ ФОТО — СНАЧАЛА ОЧИЩАЕМ СТАРОЕ (для множественных свойств)
        if (patch.fileName != null && patch.fileBase64 != null) {
            try {
                val clearParams = BitrixParamsBuilder.build(
                    mapOf(
                        "IBLOCK_TYPE_ID" to IBLOCK_TYPE_LISTS,
                        "IBLOCK_ID" to IBLOCK_ID_LISTS,
                        "ELEMENT_ID" to item.id,
                        "FIELDS" to mapOf(
                            "NAME" to item.name, // Название обязательно при любом обновлении!
                            "PROPERTY_115" to "" // Очистка свойства
                        )
                    )
                )
                api.listElementUpdate(clearParams)
                println("DEBUG_B24: Свойство PROPERTY_115 очищено перед загрузкой нового фото.")
            } catch (e: Exception) {
                println("DEBUG_B24: Не удалось очистить PROPERTY_115: ${e.message}")
            }
        }

        val fields = mutableMapOf<String, Any?>()
        
        // 1. Название — обязательно для B24
        fields["NAME"] = item.name
        
        // 2. Статус — то, что мы меняем
        fields["PROPERTY_109"] = patch.status.code
        
        // 3. Маршрут — ПЕРЕДАЕМ ВСЕГДА, чтобы не затереть в Битриксе
        if (item.routeCodes.isNotEmpty()) {
            fields["PROPERTY_111"] = item.routeCodes
        }
        
        // 4. Кто обработал
        val handledBy = patch.handledByContactId ?: item.handledByContactId
        if (!handledBy.isNullOrBlank()) {
            fields["PROPERTY_113"] = handledBy
        }
        
        // 5. Причина поломки
        val breakage = patch.breakageReason ?: item.breakageReason
        if (!breakage.isNullOrBlank()) {
            fields["PROPERTY_119"] = breakage
        }

        // 6. Порядковый номер
        if (!item.property107.isNullOrBlank()) {
            fields["PROPERTY_107"] = item.property107
        }

        // 7. Фотография
        if (patch.fileName != null && patch.fileBase64 != null) {
            // Возвращаемся к самому простому формату. Если он дублирует — будем решать иначе,
            // но сейчас важно восстановить саму загрузку файлов.
            fields["PROPERTY_115"] = mapOf(
                "fileData" to listOf(patch.fileName, patch.fileBase64)
            )
        }

        val params = BitrixParamsBuilder.build(
            mapOf(
                "IBLOCK_TYPE_ID" to IBLOCK_TYPE_LISTS,
                "IBLOCK_ID" to IBLOCK_ID_LISTS,
                "ELEMENT_ID" to item.id,
                "FIELDS" to fields
            )
        )
        val response = api.listElementUpdate(params)
        if (response.error != null) throw Exception("Bitrix Error: ${response.errorDescription ?: response.error}")
        
        val success = isSuccess(response.result)
        
        // УДАЛЕНИЕ ФОТО ПОСЛЕ УСПЕШНОЙ ЗАГРУЗКИ
        if (success && !patch.localFilePath.isNullOrBlank()) {
            try {
                val file = File(patch.localFilePath)
                if (file.exists()) {
                    file.delete()
                    // Также очищаем путь в базе данных, чтобы не пытаться показать удаленное фото
                    dao.updateAddressStatus(item.id, patch.status, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return success
    }

    override suspend fun fetchAndCacheAddresses(route: List<String>, isWorker: Boolean) {
        // Загружаем ВСЕ нужные статусы для каждой роли
        val statusList = if (isWorker) {
            listOf(AddressStatus.SENT_TO_REPAIR.code, AddressStatus.SKIPPED_WORKER.code, AddressStatus.REPAIR_DONE.code)
        } else {
            // Монтажнику нужны: Новые, Пропущенные, Загруженные И те, что в Ремонте (R и R1)
            listOf(
                AddressStatus.NEW.code, 
                AddressStatus.SKIPPED_INSPECTOR.code, 
                AddressStatus.PHOTO_UPLOADED.code, 
                AddressStatus.SENT_TO_REPAIR.code,
                AddressStatus.REPAIR_DONE.code
            )
        }

        val allElements = mutableListOf<AddressEntity>()
        var isAnyRequestSuccessful = false

        for (status in statusList) {
            try {
                val routeFilter = if (route.size == 1) route[0] else route
                val params = BitrixParamsBuilder.build(
                    mapOf(
                        "IBLOCK_TYPE_ID" to IBLOCK_TYPE_LISTS,
                        "IBLOCK_ID" to IBLOCK_ID_LISTS,
                        "ELEMENT_ORDER" to mapOf("PROPERTY_109" to "ASC", "PROPERTY_107" to "ASC"),
                        "FILTER" to mapOf(
                            "PROPERTY_111" to routeFilter,
                            "PROPERTY_109" to status
                        )
                    )
                )
                
                val response = api.listElementGet(params)
                isAnyRequestSuccessful = true // Запрос прошел, сервер ответил
                
                if (response.result != null) {
                    allElements.addAll(response.result.map { it.toEntity(isWorker) })
                }
            } catch (e: Exception) {
                println("DEBUG_B24: Ошибка при запросе ($status): ${e.message}")
                // Если хоть один запрос упал по сети, мы не считаем синхронизацию успешной
            }
        }

        // ВАЖНО: При обновлении базы мы должны сохранить локальные пути к фото,
        // которые еще не синхронизированы, иначе они пропадут из списка "Загруженные"
        if (isAnyRequestSuccessful) {
            val currentCached = dao.getAllForRole(isWorker)
            val allPending = dao.getAllPendingUploads()
            
            val mergedElements = allElements.map { newElement ->
                val localMatch = currentCached.find { it.id == newElement.id }
                val hasPending = allPending.any { it.addressId == newElement.id }
                
                // ЛОГИКА СИНХРОНИЗАЦИИ:
                if (newElement.status == AddressStatus.NEW && !hasPending) {
                    // Если сервер говорит "Новый" и в очереди ничего нет — сбрасываем локальное фото
                    newElement.copy(localPhotoPath = null)
                } else if (hasPending || (localMatch != null && !localMatch.localPhotoPath.isNullOrBlank())) {
                    // Если есть задача в очереди или есть локальное фото, сохраняем наше состояние
                    newElement.copy(
                        status = localMatch?.status ?: newElement.status,
                        localPhotoPath = localMatch?.localPhotoPath,
                        breakageReason = localMatch?.breakageReason ?: newElement.breakageReason
                    )
                } else {
                    newElement
                }
            }

            dao.clearAddresses(isWorker)
            if (mergedElements.isNotEmpty()) {
                dao.insertAddresses(mergedElements)
            }
            println("DEBUG_B24: Синхронизация завершена. Элементов: ${mergedElements.size}")
        }
    }

    override suspend fun addLead(deviceUserId: String, firstName: String, lastName: String, email: String): Int {
        // 1. ПРОВЕРКА: Есть ли уже лид с таким Email
        try {
            val searchParams = BitrixParamsBuilder.build(
                mapOf(
                    "FILTER" to mapOf("EMAIL" to email),
                    "SELECT" to listOf("ID", "STATUS_ID")
                )
            )
            val existingLeads = api.leadList(searchParams).result
            
            // Ищем лид, который не завершен (не сконвертирован в контакт и не проигран)
            // По умолчанию в B24 'CONVERTED' и 'JUNK' — финальные.
            val activeLead = existingLeads?.firstOrNull { 
                val status = it["STATUS_ID"] ?: ""
                status != "CONVERTED" && status != "JUNK"
            }

            if (activeLead != null) {
                val existingId = activeLead["ID"]?.toString()?.split(".")?.get(0)?.toIntOrNull() ?: 0
                if (existingId > 0) {
                    println("DEBUG_B24: Нашел активный лид $existingId для email $email. Добавляю комментарий.")
                    
                    val commentParams = BitrixParamsBuilder.build(
                        mapOf(
                            "fields" to mapOf(
                                "ENTITY_ID" to existingId,
                                "ENTITY_TYPE" to "lead",
                                "COMMENT" to "Получено повторное обращение на регистрацию.\nФИО: $firstName $lastName\nEmail: $email"
                            )
                        )
                    )
                    api.timelineCommentAdd(commentParams)
                    return existingId
                }
            }
        } catch (e: Exception) {
            println("DEBUG_B24: Ошибка при поиске существующего лида: ${e.message}")
        }

        // 2. Если активного лида нет — СОЗДАЕМ НОВЫЙ
        val params = BitrixParamsBuilder.build(
            mapOf(
                "fields" to mapOf(
                    "TITLE" to "Заявка на регистрацию пользователя $firstName $lastName DeviceID= $deviceUserId",
                    "NAME" to firstName,
                    "LAST_NAME" to lastName,
                    "UF_CRM_1660057795899" to deviceUserId,
                    "ASSIGNED_BY_ID" to "1",
                    "EMAIL" to listOf(mapOf("VALUE" to email, "VALUE_TYPE" to "WORK"))
                ),
                "params" to mapOf("REGISTER_SONET_EVENT" to "Y")
            )
        )
        val response = api.leadAdd(params)
        if (response.error != null) {
            throw Exception("Bitrix Error: ${response.errorDescription ?: response.error}")
        }
        val result = response.result
        val leadId = when (result) {
            is Number -> result.toInt()
            is String -> result.split(".").get(0).toIntOrNull() ?: 0
            else -> 0
        }

        // ВТОРОЙ ШАГ: Обновляем заголовок лида, чтобы в нем был его собственный ID
        if (leadId > 0) {
            try {
                val updateParams = BitrixParamsBuilder.build(
                    mapOf(
                        "ID" to leadId,
                        "FIELDS" to mapOf(
                            "TITLE" to "Заявка на регистрацию пользователя $firstName $lastName DeviceID= $leadId",
                            "UF_CRM_1660057795899" to leadId.toString()
                        )
                    )
                )
                api.leadUpdate(updateParams)
            } catch (e: Exception) {
                println("DEBUG_B24: Не удалось обновить заголовок лида: ${e.message}")
            }
        }

        return leadId
    }

    private fun isSuccess(result: Any?): Boolean {
        if (result == null) return false
        if (result is Boolean) return result
        if (result is Number) return result.toInt() >= 1
        if (result is String) return result == "true" || result == "1" || result.startsWith("1")
        return false
    }

    override suspend fun getLeadId(deviceUserId: String): Int {
        val params = BitrixParamsBuilder.build(
            mapOf(
                "FILTER" to mapOf(
                    "UF_CRM_1660057795899" to deviceUserId
                ),
                "SELECT" to listOf("ID", "STATUS_ID")
            )
        )
        return try {
            val response = api.leadList(params)
            // Возвращаем ID только АКТИВНОГО лида (который еще не закрыт)
            val activeLead = response.result?.firstOrNull { 
                val status = it["STATUS_ID"]?.toString() ?: ""
                status != "CONVERTED" && status != "JUNK"
            }
            activeLead?.get("ID")?.toString()?.split(".")?.get(0)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun getCachedContact(deviceUserId: String): Contact? {
        val config = dao.getConfig(deviceUserId) ?: return null
        return Contact(
            id = config.contactId,
            state = null,
            route = Converters().toStringList(config.routeJson),
            role = UserRole.valueOf(config.role)
        )
    }

    override suspend fun saveContactToCache(deviceUserId: String, contact: Contact) {
        dao.saveConfig(
            UserConfigEntity(
                deviceUserId = deviceUserId,
                contactId = contact.id,
                role = contact.role.name,
                routeJson = Converters().fromStringList(contact.route)
            )
        )
    }

    override suspend fun closeLeadIfExists(deviceUserId: String) {
        try {
            val params = BitrixParamsBuilder.build(
                mapOf(
                    "FILTER" to mapOf("UF_CRM_1660057795899" to deviceUserId),
                    "SELECT" to listOf("ID", "STATUS_ID")
                )
            )
            val leads = api.leadList(params).result
            leads?.forEach { lead ->
                val status = lead["STATUS_ID"]?.toString() ?: ""
                if (status != "CONVERTED" && status != "JUNK") {
                    val id = lead["ID"]?.toString()?.split(".")?.get(0)
                    if (id != null) {
                        val updateParams = BitrixParamsBuilder.build(
                            mapOf(
                                "ID" to id,
                                "FIELDS" to mapOf("STATUS_ID" to "CONVERTED")
                            )
                        )
                        api.leadUpdate(updateParams)
                        println("DEBUG_B24: Лид $id автоматически закрыт после входа.")
                    }
                }
            }
        } catch (e: Exception) {
            println("DEBUG_B24: Ошибка при автоматическом закрытии лида: ${e.message}")
        }
    }
}

private fun Any?.toB24String(): String? {
    if (this == null) return null
    if (this is String) return this.replace(".0", "") // Очистка от Double-формата
    if (this is Map<*, *>) {
        if (this.isEmpty()) return null
        // Bitrix часто присылает свойства как {"ID_ЗНАЧЕНИЯ": "САМО_ЗНАЧЕНИЕ"}
        // Берем первое попавшееся значение из мапы
        return this.values.firstOrNull()?.toB24String()
    }
    val str = this.toString()
    return if (str.endsWith(".0")) str.substringBefore(".0") else str
}

private fun Any?.toB24List(): List<String> {
    if (this == null) return emptyList()
    if (this is List<*>) return this.mapNotNull { it.toB24String() }
    if (this is Map<*, *>) {
        if (this.isEmpty()) return emptyList()
        // Для списков (например, маршрутов) берем все значения из мапы
        return this.values.mapNotNull { it.toB24String() }
    }
    val single = this.toB24String()
    return if (single != null) listOf(single) else emptyList()
}

private fun ListElementDto.toDomain(): AddressItem = AddressItem(
    id = id,
    name = name.orEmpty(),
    property107 = property107.toB24String(),
    status = AddressStatus.entries.firstOrNull { it.code == property109.toB24String() },
    routeCodes = property111.toB24List(),
    handledByContactId = property113.toB24String(),
    breakageReason = property119.toB24String(),
    timestampX = timestampX
)

private fun ListElementDto.toEntity(isWorker: Boolean): AddressEntity = AddressEntity(
    id = id,
    name = name.orEmpty(),
    property107 = property107.toB24String(),
    status = AddressStatus.entries.firstOrNull { it.code == property109.toB24String() },
    routeCodes = property111.toB24List(),
    handledByContactId = property113.toB24String(),
    breakageReason = property119.toB24String(),
    timestampX = timestampX,
    isWorkerList = isWorker
)

private fun AddressEntity.toDomain(): AddressItem = AddressItem(
    id = id,
    name = name,
    property107 = property107,
    status = status,
    routeCodes = routeCodes,
    handledByContactId = handledByContactId,
    breakageReason = breakageReason,
    timestampX = timestampX,
    localPhotoPath = localPhotoPath
)
