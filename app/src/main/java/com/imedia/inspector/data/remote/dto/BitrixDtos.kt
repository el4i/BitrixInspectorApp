package com.imedia.inspector.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Bitrix24 REST всегда оборачивает ответ в {"result": ..., "time": {...}}.
 * В PHP это делала строка: `$response['result']`.
 */
data class B24EnvelopeDto<T>(
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null
)

/**
 * crm.contact.list — соответствует полям, которые запрашивал getContact():
 * ID, UF_CRM_1784014618374 (MAX/Telegram user id), UF_CRM_1659870645432 (state),
 * UF_CRM_1659879474690 (route, массив), UF_CRM_1662016534407 (position/роль).
 */
data class ContactDto(
    @SerializedName("ID") val id: String,
    @SerializedName("UF_CRM_1784014618374") val maxUserId: String? = null,
    @SerializedName("UF_CRM_1659870645432") val state: Any? = null,
    @SerializedName("UF_CRM_1659879474690") val route: Any? = null, // Заменили на Any? для безопасности
    @SerializedName("UF_CRM_1662016534407") val position: Any? = null // И здесь тоже Any?
)

/**
 * Элемент бизнес-процесса (IBLOCK_ID = 29, "lists").
 * Соответствует ассоциативному массиву $arRes, который собирала getlist()/getReplist().
 *
 * PROPERTY_107 — сортировка / вспомогательное поле
 * PROPERTY_109 — статус адреса: N (новый), S (пропущен инспектором),
 *                R (отправлен на ремонт), RS (пропущен ремонтником), R1 (ремонт выполнен), Y (фото загружено)
 * PROPERTY_111 — код маршрута/адреса (route), массив
 * PROPERTY_113 — ID контакта, который обработал элемент
 * PROPERTY_115 — прикреплённый файл (fileData: [имя, base64])
 * PROPERTY_117 — резерв (не используется в бизнес-логике, но передаётся туда/обратно)
 * PROPERTY_119 — причина поломки / текст действия ремонтника
 */
data class ListElementDto(
    @SerializedName("ID") val id: String,
    @SerializedName("NAME") val name: String? = null,
    @SerializedName("PROPERTY_107") val property107: Any? = null,
    @SerializedName("PROPERTY_109") val property109: Any? = null,
    @SerializedName("PROPERTY_111") val property111: Any? = null,
    @SerializedName("PROPERTY_113") val property113: Any? = null,
    @SerializedName("PROPERTY_115") val property115: Any? = null,
    @SerializedName("PROPERTY_117") val property117: Any? = null,
    @SerializedName("PROPERTY_119") val property119: Any? = null,
    @SerializedName("TIMESTAMP_X") val timestampX: String? = null
)

/**
 * Файл в формате Bitrix24 ("fileData": [имя_файла, base64]),
 * как формировал PHP: ['fileData' => [filename, base64_encode(...)]]
 */
data class FileDataDto(
    @SerializedName("fileData") val fileData: List<String>? = null
)
