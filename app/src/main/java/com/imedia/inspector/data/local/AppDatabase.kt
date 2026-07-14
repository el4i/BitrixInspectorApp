package com.imedia.inspector.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.imedia.inspector.domain.model.AddressStatus

import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {
    @Query("SELECT * FROM addresses WHERE isWorkerList = :isWorker AND id = :addressId LIMIT 1")
    suspend fun getAddressById(isWorker: Boolean, addressId: String): AddressEntity?

    @Query("SELECT * FROM addresses WHERE isWorkerList = :isWorker")
    fun observeAllForRole(isWorker: Boolean): Flow<List<AddressEntity>>

    @Query("SELECT * FROM addresses WHERE isWorkerList = :isWorker")
    suspend fun getAllForRole(isWorker: Boolean): List<AddressEntity>

    @Query("SELECT * FROM addresses WHERE isWorkerList = :isWorker AND status = :status LIMIT 1")
    suspend fun getNextAddress(isWorker: Boolean, status: AddressStatus): AddressEntity?

    @Query("SELECT * FROM addresses WHERE isWorkerList = :isWorker AND status = :status")
    suspend fun getAllByStatus(isWorker: Boolean, status: AddressStatus): List<AddressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddresses(addresses: List<AddressEntity>)

    @Query("DELETE FROM addresses WHERE isWorkerList = :isWorker")
    suspend fun clearAddresses(isWorker: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingUpload(upload: PendingUploadEntity): Long

    @Query("SELECT * FROM pending_uploads WHERE localId = :id")
    suspend fun getPendingUploadById(id: Long): PendingUploadEntity?

    @Query("SELECT * FROM pending_uploads")
    suspend fun getAllPendingUploads(): List<PendingUploadEntity>

    @Query("DELETE FROM pending_uploads WHERE localId = :id")
    suspend fun deletePendingUpload(id: Long)

    @Query("UPDATE addresses SET status = :status, localPhotoPath = :photoPath WHERE id = :addressId")
    suspend fun updateAddressStatus(addressId: String, status: AddressStatus, photoPath: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: UserConfigEntity)

    @Query("SELECT * FROM user_config WHERE deviceUserId = :id LIMIT 1")
    suspend fun getConfig(id: String): UserConfigEntity?
}

@Database(entities = [AddressEntity::class, PendingUploadEntity::class, UserConfigEntity::class], version = 6)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun addressDao(): AddressDao
}
