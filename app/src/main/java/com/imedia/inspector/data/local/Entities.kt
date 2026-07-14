package com.imedia.inspector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.imedia.inspector.domain.model.AddressStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "addresses")
data class AddressEntity(
    @PrimaryKey val id: String,
    val name: String,
    val property107: String?,
    val status: AddressStatus?,
    val routeCodes: List<String>,
    val handledByContactId: String?,
    val breakageReason: String?,
    val timestampX: String?,
    val isWorkerList: Boolean, // true если из getRepairList, false если из getAddressList
    val localPhotoPath: String? = null // Путь к фото в памяти телефона
)

@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val addressId: String,
    val addressName: String, 
    val status: AddressStatus,
    val routeJson: String = "[]", // Перенесли ниже и добавили значение по умолчанию
    val handledByContactId: String?,
    val breakageReason: String?,
    val fileName: String?,
    val photoFilePath: String?, // путь к локальному файлу
    val property107: String? = null // Порядковый номер
)

@Entity(tableName = "user_config")
data class UserConfigEntity(
    @PrimaryKey val deviceUserId: String,
    val contactId: String?,
    val role: String,
    val routeJson: String
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Gson().toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromStatus(status: AddressStatus?): String? = status?.name

    @TypeConverter
    fun toStatus(name: String?): AddressStatus? = name?.let { AddressStatus.valueOf(it) }
}
