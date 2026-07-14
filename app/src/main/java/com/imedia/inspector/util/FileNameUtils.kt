package com.imedia.inspector.util

import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Соответствует PHP:
 *   $forbidden = '\/:*?"<>|+%!@';
 *   '('.reset($list['PROPERTY_111']).')_'
 *      .preg_replace("/[${forbidden}]/","_",$list['name'])
 *      .'_'.date('d-m-Y H:i:s').'.'.$extension
 */
object FileNameUtils {

    private const val FORBIDDEN_CHARS = "\\/:*?\"<>|+%!@"
    private val DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

    fun sanitize(name: String): String {
        val sb = StringBuilder()
        for (ch in name) {
            sb.append(if (FORBIDDEN_CHARS.contains(ch)) '_' else ch)
        }
        return sb.toString()
    }

    /**
     * @param routeCode  первый элемент PROPERTY_111 (аналог reset($list['PROPERTY_111']))
     * @param addressName NAME адреса
     * @param extension  расширение файла без точки (jpg/png/...)
     */
    fun buildFileName(routeCode: String, addressName: String, extension: String): String {
        val safeName = sanitize(addressName)
        val date = DATE_FORMAT.format(Date())
        return "($routeCode)_${safeName}_$date.$extension"
    }

    fun extensionFromFile(file: File): String {
        val ext = file.extension
        return ext.ifBlank { "jpg" }
    }

    fun fileToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
