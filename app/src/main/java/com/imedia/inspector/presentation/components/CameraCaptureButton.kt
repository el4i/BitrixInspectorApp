package com.imedia.inspector.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import android.os.Environment
import android.media.MediaScannerConnection
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
            // Уведомляем систему о новом файле, чтобы он появился в галерее
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            previewUri = Uri.fromFile(file)
            onPhotoTaken(file)
        }
    }

    val settingResultRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Если пользователь нажал "ОК" в системном окне GPS
            val file = createTempImageFile(context)
            pendingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        } else {
            Toast.makeText(context, "Без GPS работа невозможна!", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraWithCheck() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled) {
            val file = createTempImageFile(context)
            pendingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launcher.launch(uri)
        } else {
            // Пытаемся принудительно запросить включение GPS через системный диалог
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
            val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            val client = LocationServices.getSettingsClient(context)
            client.checkLocationSettings(builder.build()).addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        settingResultRequest.launch(intentSenderRequest)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        
        if (cameraGranted && locationGranted) {
            launchCameraWithCheck()
        } else {
            Toast.makeText(context, "Необходимы разрешения на камеру и GPS!", Toast.LENGTH_SHORT).show()
        }
    }

    Column {
        Button(
            onClick = {
                val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

                if (hasCameraPermission && hasLocationPermission) {
                    launchCameraWithCheck()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
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
    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "iMedia_Inspector"
    ).apply { mkdirs() }
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
    return File(dir, "IMG_${timestamp}.jpg")
}
