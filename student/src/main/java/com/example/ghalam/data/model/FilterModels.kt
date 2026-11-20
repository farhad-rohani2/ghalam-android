package com.example.ghalam.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class FiltersResponse(val filters: List<Filter>)

/** How a filter behaves in the UI (mirrors the site's Filter.vue component types). */
enum class FilterKind { SINGLE, MULTI, UNSUPPORTED }

@Serializable
data class Filter(
    val key: String,
    val name: String = "",
    val type: String = "",
    // options can be a JSON object ({"0":{...}}) or an array, so keep it raw
    val options: JsonElement? = null,
    @SerialName("filter_settings") val filterSettings: JsonElement? = null,
) {
    /** Same rule as Filter.vue: filter_settings[0].type wins over type. */
    val effectiveType: String
        get() {
            val first = (filterSettings as? JsonArray)?.firstOrNull() as? JsonObject
            return (first?.get("type") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: type
        }

    val kind: FilterKind
        get() = when (effectiveType) {
            "Select", "Radio", "TagInline" -> FilterKind.SINGLE
            "Checkbox", "Multiselect", "Tags", "TagsInline" -> FilterKind.MULTI
            // Range / Colors / free text are not supported in the app
            "Range", "Float", "Colors" -> FilterKind.UNSUPPORTED
            else -> if (parsedOptions().isNotEmpty()) FilterKind.SINGLE else FilterKind.UNSUPPORTED
        }

    fun parsedOptions(): List<FilterOption> {
        val el = options ?: return emptyList()
        val items: Collection<JsonElement> = when (el) {
            is JsonObject -> el.values
            is JsonArray -> el
            else -> return emptyList()
        }
        return items.mapNotNull { item ->
            when (item) {
                is JsonObject -> {
                    val id = item["id"].text() ?: item["value"].text() ?: return@mapNotNull null
                    FilterOption(id, item["label"].text() ?: item["name"].text() ?: id)
                }
                is JsonPrimitive -> item.contentOrNull?.let { FilterOption(it, it) }
                else -> null
            }
        }
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
}

data class FilterOption(val id: String, val label: String)
