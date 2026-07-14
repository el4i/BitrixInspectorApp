package com.imedia.inspector.data.remote

import com.imedia.inspector.data.remote.dto.B24EnvelopeDto
import com.imedia.inspector.data.remote.dto.ContactDto
import com.imedia.inspector.data.remote.dto.ListElementDto
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Все методы Bitrix24 REST в оригинальном PHP вызывались через
 * callB24Method($method, $params) -> curl POST, application/x-www-form-urlencoded,
 * тело собиралось через http_build_query($params).
 *
 * PHP умеет отправлять вложенные массивы (FILTER[...], SELECT[]=..., FIELDS[...])
 * в виде "плоских" ключей вида FILTER[PROPERTY_111][]=1&FILTER[PROPERTY_111][]=2.
 * Retrofit с @FieldMap этого сам не сделает, поэтому все параметры собираются
 * заранее в BitrixParamsBuilder (см. util/BitrixParamsBuilder.kt) в Map<String, String>
 * с уже "плоскими" ключами, и передаются сюда как обычная FormUrlEncoded карта.
 *
 * Базовый URL (Retrofit baseUrl):
 *   https://ra-imedia1.bitrix24.ru/rest/591/hl3vsqvtd6iaig0b/
 */
interface Bitrix24ApiService {

    // ---- crm.contact.list -> getContact($maxid) ----
    @FormUrlEncoded
    @POST("crm.contact.list.json")
    suspend fun contactList(@FieldMap params: Map<String, String>): B24EnvelopeDto<List<ContactDto>>

    // ---- crm.contact.update -> setState($conId, $state) ----
    @FormUrlEncoded
    @POST("crm.contact.update.json")
    suspend fun contactUpdate(@FieldMap params: Map<String, String>): B24EnvelopeDto<Boolean>

    // ---- lists.element.get -> getlist() / getReplist() ----
    @FormUrlEncoded
    @POST("lists.element.get.json")
    suspend fun listElementGet(@FieldMap params: Map<String, String>): B24EnvelopeDto<List<ListElementDto>>

    // ---- lists.element.update -> updList($list) ----
    @FormUrlEncoded
    @POST("lists.element.update.json")
    suspend fun listElementUpdate(@FieldMap params: Map<String, String>): B24EnvelopeDto<Any>

    // ---- crm.lead.add -> addLead($telegramId, $username) ----
    @FormUrlEncoded
    @POST("crm.lead.add.json")
    suspend fun leadAdd(@FieldMap params: Map<String, String>): B24EnvelopeDto<Any>

    // ---- crm.lead.update ----
    @FormUrlEncoded
    @POST("crm.lead.update.json")
    suspend fun leadUpdate(@FieldMap params: Map<String, String>): B24EnvelopeDto<Boolean>

    // ---- crm.lead.list -> getLead($telegramId) ----
    @FormUrlEncoded
    @POST("crm.lead.list.json")
    suspend fun leadList(@FieldMap params: Map<String, String>): B24EnvelopeDto<List<Map<String, String>>>

    // ---- crm.timeline.comment.add ----
    @FormUrlEncoded
    @POST("crm.timeline.comment.add.json")
    suspend fun timelineCommentAdd(@FieldMap params: Map<String, String>): B24EnvelopeDto<Int>
}
