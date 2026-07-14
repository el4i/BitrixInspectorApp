package com.imedia.inspector.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.imedia.inspector.data.local.AppDatabase
import com.imedia.inspector.data.remote.Bitrix24ApiService
import com.imedia.inspector.data.remote.SyncWorker
import com.imedia.inspector.data.repository.BitrixRepository
import com.imedia.inspector.data.repository.BitrixRepositoryImpl
import com.imedia.inspector.presentation.viewmodel.MainViewModel
import com.imedia.inspector.util.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.content.Context

object AppModule {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(appContext)
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "bitrix_cache.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private const val BASE_URL = "https://reklama-lift-kazan.ru/bot/bx-proxy.php/"
    private const val PROXY_SECRET_KEY = "MySuperSecretKey_123456789"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Proxy-Key", PROXY_SECRET_KEY)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val bitrixApi: Bitrix24ApiService by lazy {
        retrofit.create(Bitrix24ApiService::class.java)
    }

    val repository: BitrixRepository by lazy {
        BitrixRepositoryImpl(bitrixApi, database.addressDao())
    }

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "SyncWorkerUnique",
            androidx.work.ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}

/**
 * deviceUserId — идентификатор конкретного сотрудника на этом устройстве
 * (например, из FirebaseInstallations, ANDROID_ID, либо логин, выданный при онбординге).
 * Он играет роль user_id из MAX-бота и хранится в Bitrix в UF_CRM_1784014618374.
 */
class MainViewModelFactory(
    private val displayName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(AppModule.repository, AppModule.sessionManager, displayName) as T
    }
}
