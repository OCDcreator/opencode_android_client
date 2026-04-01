package com.yage.opencode_client.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable(with = SessionStatusResponseSerializer::class)
data class SessionStatusResponse(val entries: Map<String, SessionStatus>)

private object SessionStatusResponseSerializer : KSerializer<SessionStatusResponse> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
    override fun serialize(encoder: Encoder, value: SessionStatusResponse) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = JsonObject(value.entries.mapValues { jsonEncoder.json.encodeToJsonElement(SessionStatus.serializer(), it.value) })
        jsonEncoder.encodeJsonElement(jsonObject)
    }
    override fun deserialize(decoder: Decoder): SessionStatusResponse {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val entries = jsonObject.mapValues { jsonDecoder.json.decodeFromJsonElement(SessionStatus.serializer(), it.value) }
        return SessionStatusResponse(entries)
    }
}
