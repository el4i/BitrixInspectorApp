package com.imedia.inspector.domain.model

/** position (UF_CRM_1662016534407) в исходном боте */
enum class UserRole(val code: String) {
    MONTAJNIK("225"),
    WORKER("227"),
    UNKNOWN("");

    companion object {
        fun fromCode(code: String?): UserRole =
            entries.firstOrNull { it.code == code && code.isNotEmpty() } ?: UNKNOWN
    }
}

/** Соответствует значениям PROPERTY_109 в разных ветках PHP-скрипта. */
enum class AddressStatus(val code: String) {
    NEW("N"),                 // новый адрес для инспектора
    SKIPPED_INSPECTOR("S"),   // "Лифт не работает" — пропущен инспектором
    SENT_TO_REPAIR("R"),      // стенд сломан -> отправлен на ремонт (инспектор)
    PHOTO_UPLOADED("Y"),      // фото рекламы загружено инспектором
    SKIPPED_WORKER("RS"),     // пропущен ремонтником
    REPAIR_DONE("R1")         // фото ремонта загружено ремонтником
}

/** Причины поломки стенда — соответствуют кнопкам "Замена стекла" и т.д. */
enum class BreakageReason(val label: String) {
    GLASS_REPLACEMENT("Замена стекла"),
    BAR_AND_GLASS_REPLACEMENT("Замена планки и стекла"),
    STAND_REPLACEMENT("Замена стенда")
}

/** Причины по которым лифт не работает или недоступен */
enum class ElevatorSkipReason(val label: String) {
    REPAIR("Лифт на ремонте"),
    POWER_OUTAGE("Отключен свет"),
    MAINTENANCE("Тех. обслуживание"),
    NO_ACCESS("Нет доступа в подъезд"),
    OTHER("Другое")
}

/** Bitrix contact (аналог $bxContact массива, который собирала getContact()). */
data class Contact(
    val id: String?,
    val state: String?,
    val route: List<String>,
    val role: UserRole
) {
    val isRegistered: Boolean get() = !id.isNullOrEmpty()
}

/** Элемент списка адресов — аналог $arRes из getlist()/getReplist(). */
data class AddressItem(
    val id: String,
    val name: String,
    val property107: String?,
    val status: AddressStatus?,
    val routeCodes: List<String>,
    val handledByContactId: String?,
    val breakageReason: String?, // PROPERTY_119, актуально для роли WORKER
    val timestampX: String?,
    val localPhotoPath: String? = null, // Для отображения в оффлайне сразу после съемки
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPendingSync: Boolean = false
)

/** Верхнеуровневое состояние экрана — заменяет числовые state (2,6,9,12,16,19...) из PHP. */
sealed interface AppScreenState {
    data object Loading : AppScreenState
    data object LoggedOut : AppScreenState
    data object NeedRegistration : AppScreenState
    data object PendingRegistration : AppScreenState
    data class InspectorFlow(
        val addresses: List<AddressItem>, // Весь список
        val selected: AddressItem?,
        val hasSkipped: Boolean,
        val mode: InspectorMode,
        val selectedTab: Int = 0 // 0 - План, 1 - Выполнено
    ) : AppScreenState
    data class WorkerFlow(
        val addresses: List<AddressItem>, // Весь список
        val selected: AddressItem?,
        val hasSkipped: Boolean,
        val mode: WorkerMode,
        val selectedTab: Int = 0 // 0 - План, 1 - Выполнено
    ) : AppScreenState
    data class Error(val message: String) : AppScreenState
}

enum class InspectorMode { LIST_EMPTY, AWAITING_PHOTO, AWAITING_PHOTO_SKIPPED, CHOOSING_BREAKAGE }
enum class WorkerMode { LIST_EMPTY, AWAITING_PHOTO, AWAITING_PHOTO_SKIPPED }
