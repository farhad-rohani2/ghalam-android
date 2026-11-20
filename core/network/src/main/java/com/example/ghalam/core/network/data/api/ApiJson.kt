package com.example.ghalam.core.network.data.api

import kotlinx.serialization.json.Json

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true // pageId has a default value and must still be sent
}
