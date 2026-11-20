package com.example.ghalam.core.network.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The login answer is the user object; the token is at the top level or under "data". */
fun JsonObject.tokenOrNull(): String? =
    (get("token") as? JsonPrimitive)?.contentOrNull
        ?: ((get("data") as? JsonObject)?.get("token") as? JsonPrimitive)?.contentOrNull
