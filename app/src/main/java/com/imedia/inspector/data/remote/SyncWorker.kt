package com.imedia.inspector.data.remote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.imedia.inspector.data.repository.AddressUpdatePatch
import com.imedia.inspector.di.AppModule
import com.imedia.inspector.domain.model.AddressItem
import com.imedia.inspector.util.FileNameUtils
import java.io.File

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionManager = AppModule.sessionManager
        val isManualRun = inputData.getString("sync_type") == "manual"
        val isAutoUploadEnabled = sessionManager.isAutoUploadEnabled()

        val dao = AppModule.database.addressDao()
        val pendingList = dao.getAllPendingUploads()

        if (pendingList.isEmpty()) return Result.success()

        var allSuccess = true
        for (pending in pendingList) {
            try {
                // ЛОГИКА РАЗДЕЛЕНИЯ (Metadata vs Photo):
                val hasPhoto = !pending.photoFilePath.isNullOrBlank()
                
                // Пропускаем загрузку фото, если автозагрузка выключена И это не ручной запуск.
                // При этом "Пропуски" (где нет фото) уходят ВСЕГДА.
                if (hasPhoto && !isAutoUploadEnabled && !isManualRun) {
                    println("DEBUG_B24: SyncWorker - Пропуск фото для ${pending.addressId} (ждем ручной выгрузки).")
                    continue
                }

                // ПРОВЕРКА: Если элемент уже удален (например, updateAddress успел отправить)
                val stillExists = dao.getPendingUploadById(pending.localId) != null
                if (!stillExists) {
                    println("DEBUG_B24: SyncWorker - Элемент ${pending.addressId} уже отправлен или удален, пропускаем.")
                    continue
                }

                println("DEBUG_B24: SyncWorker - Обработка адреса ID=${pending.addressId} (${pending.addressName}). Файл: ${pending.photoFilePath}")

                val photoFile = pending.photoFilePath?.let { File(it) }
                val base64 = if (photoFile != null && photoFile.exists()) {
                    FileNameUtils.fileToBase64(photoFile)
                } else {
                    null
                }

                val patch = AddressUpdatePatch(
                    status = pending.status,
                    handledByContactId = pending.handledByContactId,
                    breakageReason = pending.breakageReason,
                    fileName = pending.fileName,
                    fileBase64 = base64,
                    latitude = pending.latitude,
                    longitude = pending.longitude
                )

                val fakeItem = AddressItem(
                    id = pending.addressId,
                    name = pending.addressName, // Теперь используем сохраненное имя
                    code = pending.addressCode,
                    property107 = pending.property107,
                    status = null,
                    routeCodes = com.imedia.inspector.data.local.Converters().toStringList(pending.routeJson), // ВОССТАНАВЛИВАЕМ МАРШРУТ
                    handledByContactId = pending.handledByContactId, // ВАЖНО: передаем кто обработал
                    breakageReason = pending.breakageReason, // Передаем причину
                    timestampX = null
                )

                val success = AppModule.repository.uploadDirectly(fakeItem, patch)
                if (success) {
                    dao.deletePendingUpload(pending.localId)
                    // ОБНОВЛЯЕМ статус в основной таблице адресов, чтобы UI узнал об успехе
                    // localPhotoPath НЕ очищаем, так как хотим видеть фото в приложении/галерее.
                    // isPendingSync ставим в FALSE, так как загрузка прошла успешно.
                    dao.updateAddressStatus(pending.addressId, pending.status, pending.photoFilePath, false)

                    // ФАЙЛ БОЛЬШЕ НЕ УДАЛЯЕМ
                } else {
                    allSuccess = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                allSuccess = false
            }
        }

        return if (allSuccess) Result.success() else Result.retry()
    }
}
