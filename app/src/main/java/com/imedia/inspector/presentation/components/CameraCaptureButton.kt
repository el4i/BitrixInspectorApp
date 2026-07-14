package com.imedia.inspector.presentation.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

/**
 * Заменяет прием фото через MAX (attachments[].type == 'image') на системный вызов камеры,
 * как требует ТЗ: rememberLauncherForActivityResult(TakePicture()).
 *
 * Возвращает готовый java.io.File с полноразмерным снимком через onPhotoTaken.
 */
@Composable
fun CameraCaptureButton(
    context: Context,
    label: String = "Сделать фото",
    onPhotoTaken: (File) -> Unit
) {
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null) {
            previewUri = Uri.fromFile(file)
            onPhotoTaken(file)
        }
    }

    fun launchCamera() {
        val file = createTempImageFile(context)
        pendingFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        launcher.launch(uri)
    }

    // Запрос разрешения CAMERA перед первым снимком (Android 6.0+).
    // Если разрешение уже выдано, launchCamera() вызывается сразу, без диалога.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        }
    }

    Column {
        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    launchCamera()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(label)
        }

        previewUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Предпросмотр фото",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 8.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun createTempImageFile(context: Context): File {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    return File.createTempFile("capture_", ".jpg", dir)
}
