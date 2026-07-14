package com.imedia.inspector.util

/**
 * PHP собирал тело запроса через http_build_query($params), где $params мог содержать
 * вложенные массивы, например:
 *   ["FILTER" => ["UF_CRM_1774091874272" => "123"], "SELECT" => ["ID","STATE"]]
 * -> "FILTER[UF_CRM_1774091874272]=123&SELECT[0]=ID&SELECT[1]=STATE"
 *
 * Retrofit @FieldMap работает с плоской Map<String,String>, поэтому здесь мы
 * рекурсивно "расплющиваем" произвольный Any (Map/List/Scalar) в те же самые
 * ключи, которые ожидает Bitrix24 REST.
 */
object BitrixParamsBuilder {

    fun flatten(prefix: String, value: Any?, out: MutableMap<String, String>) {
        when (value) {
            null -> Unit
            is Map<*, *> -> value.forEach { (k, v) ->
                val key = if (prefix.isEmpty()) k.toString() else "$prefix[$k]"
                flatten(key, v, out)
            }
            is List<*> -> {
                if (value.size == 1 && prefix.contains("FILTER")) {
                    // Для фильтров Битрикса: если в списке один элемент, 
                    // передаем его как одиночное значение без индекса [0]
                    flatten(prefix, value[0], out)
                } else {
                    value.forEachIndexed { index, v ->
                        val key = "$prefix[$index]"
                        flatten(key, v, out)
                    }
                }
            }
            else -> {
                // ВАЖНО: PHP кодирует пробелы как +, Retrofit через @FieldMap кодирует их как %20.
                // Обычно Bitrix понимает оба, но для надежности проверим само значение.
                out[prefix] = value.toString()
            }
        }
    }

    /** Точка входа: params верхнего уровня — всегда Map. */
    fun build(params: Map<String, Any?>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        params.forEach { (key, value) -> flatten(key, value, out) }
        return out
    }
}
