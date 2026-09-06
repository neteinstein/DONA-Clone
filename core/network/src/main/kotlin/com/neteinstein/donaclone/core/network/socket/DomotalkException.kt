package com.neteinstein.donaclone.core.network.socket

import kotlinx.serialization.json.JsonElement

sealed class DomotalkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotConnected : DomotalkException("Not connected to the DPU")

    class ConnectFailed(
        cause: Throwable,
    ) : DomotalkException("Failed to connect to the DPU", cause)

    class RequestTimeout(
        verb: String,
        subject: String,
    ) : DomotalkException("Timed out waiting for a reply to $verb/$subject")

    class ConnectionLost(
        cause: Throwable? = null,
    ) : DomotalkException("Connection to the DPU was lost", cause)

    class MalformedResponse(
        message: String,
    ) : DomotalkException(message)

    /**
     * The hub replied with a `code` outside the success range (`code / 100 <= 3`), per protocol
     * notes §2.2's "CONFIRMED (web client)" envelope: `{code, payload, callback_id, token?}`.
     * [payload] carries the error detail the hub sent, if any (e.g. which sub-object failed).
     */
    class RequestFailed(
        val code: Int,
        val payload: JsonElement?,
    ) : DomotalkException("Request rejected by the hub (code=$code)" + (payload?.let { ": $it" } ?: ""))
}
