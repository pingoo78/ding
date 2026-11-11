package fr.pingooweb.ding.data

import kotlinx.serialization.json.Json

/** Configuration JSON commune (persistance, import/export). Tolérante aux champs inconnus. */
val DingJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}
