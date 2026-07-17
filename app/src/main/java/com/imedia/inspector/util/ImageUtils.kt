package com.imedia.inspector.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Сжимает изображение: уменьшает разрешение до макс. 1920px и ставит качество JPEG 80%.
     * Перезаписывает исходный файл.
     */
    fun compressImage(file: File) {
        try {
            val filePath = file.absolutePath
            
            // 1. Получаем ориентацию из EXIF
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            // 2. Читаем размеры без загрузки в память
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            val maxWidth = 1600
            val maxHeight = 1600
            var width = options.outWidth
            var height = options.outHeight

            // 3. Вычисляем точный коэффициент масштабирования
            var inSampleSize = 1
            if (width > maxWidth || height > maxHeight) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while (halfWidth / inSampleSize >= maxWidth && halfHeight / inSampleSize >= maxHeight) {
                    inSampleSize *= 2
                }
            }

            // 4. Загружаем уменьшенное изображение
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            val sourceBitmap = BitmapFactory.decodeFile(filePath, options) ?: return

            // Финальное точное масштабирование до maxWidth/maxHeight
            val scale = Math.min(maxWidth.toFloat() / sourceBitmap.width, maxHeight.toFloat() / sourceBitmap.height)
            val bitmap = if (scale < 1.0f) {
                val scaled = Bitmap.createScaledBitmap(
                    sourceBitmap,
                    (sourceBitmap.width * scale).toInt(),
                    (sourceBitmap.height * scale).toInt(),
                    true
                )
                sourceBitmap.recycle()
                scaled
            } else {
                sourceBitmap
            }

            // 5. Поворачиваем, если нужно
            val rotatedBitmap = rotateBitmap(bitmap, orientation)

            // 6. Сохраняем обратно в файл с качеством 70%
            FileOutputStream(file).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }

            // Очистка
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            bitmap
        }
    }
}
