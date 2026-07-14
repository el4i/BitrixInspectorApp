package com.imedia.inspector

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.imedia.inspector.di.MainViewModelFactory
import com.imedia.inspector.presentation.navigation.RootScreen
import com.imedia.inspector.presentation.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    // deviceUserId заменяет user_id из MAX-бота — уникален для устройства/сотрудника.
    private val deviceUserId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(deviceUserId = deviceUserId, displayName = "Монтажник")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.imedia.inspector.di.AppModule.init(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(context = this, viewModel = viewModel)
                }
            }
        }
    }
}
