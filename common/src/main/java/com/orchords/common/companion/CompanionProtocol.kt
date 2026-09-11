package com.orchords.common.companion

import kotlinx.serialization.Serializable

const val COMPANION_PROTOCOL_VERSION = 1
const val MAX_COMPANION_PAYLOAD_CHARS = 8 * 1024

@Serializable
enum class CompanionIntentKind {
    QUICK_QUERY,
    VOICE_START,
    VOICE_STOP,
    OPEN_ACTIVITY_ITEM,
    APPROVAL_DECISION,
    DEVICE_ACTION,
}

@Serializable
enum class CompanionAvailability {
    AVAILABLE,
    PHONE_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    REQUIRES_UNLOCK,
    UNSUPPORTED,
}

@Serializable
data class CompanionIntentEnvelope(
    val protocolVersion: Int = COMPANION_PROTOCOL_VERSION,
    val requestId: String,
    val sessionId: String,
    val idempotencyKey: String,
    val kind: CompanionIntentKind,
    val payload: String = "",
    val createdAtEpochMillis: Long,
    val requiresUnlock: Boolean = false,
) {
    fun validate() {
        require(protocolVersion == COMPANION_PROTOCOL_VERSION) { "Unsupported companion protocol" }
        require(requestId.isNotBlank() && requestId.length <= MAX_ID_CHARS) { "Invalid request id" }
        require(sessionId.isNotBlank() && sessionId.length <= MAX_ID_CHARS) { "Invalid session id" }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_ID_CHARS) { "Invalid idempotency key" }
        require(payload.length <= MAX_COMPANION_PAYLOAD_CHARS) { "Companion payload is too large" }
        require(createdAtEpochMillis >= 0) { "Invalid companion timestamp" }
    }

    private companion object {
        const val MAX_ID_CHARS = 128
    }
}

/**
 * Bounded replay guard for transient companion commands. It stores only opaque idempotency keys,
 * never transcript/action payloads. A redelivered command cannot execute twice while its key is
 * retained. Transport/session layers remain responsible for durable action receipts where needed.
 */
class CompanionReplayGuard(
    private val maxEntries: Int = 256,
) {
    init {
        require(maxEntries in 1..4096)
    }

    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun accept(idempotencyKey: String): Boolean {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 128)
        if (!seen.add(idempotencyKey)) return false
        while (seen.size > maxEntries) {
            seen.remove(seen.first())
        }
        return true
    }

    @Synchronized
    fun clear() {
        seen.clear()
    }
}
