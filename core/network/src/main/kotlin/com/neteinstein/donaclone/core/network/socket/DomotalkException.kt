package com.neteinstein.donaclone.core.network.socket

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
}
