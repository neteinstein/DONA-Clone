package com.neteinstein.donaclone.core.network.socket

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Low-level transport for the `domotalk` JSON-RPC protocol carried over a single persistent
 * WebSocket connection to `ws(s)://<host>/ws/` (see protocol notes §2). Every request is a
 * `{verb, subject, options?, filters?, token?, callback_id}` JSON object; responses are
 * correlated back purely by `callback_id`. Messages that don't correlate to a pending request
 * are treated as unsolicited live state-update pushes (§8, envelope unconfirmed) and exposed
 * via [updates] for a higher layer to interpret defensively.
 */
class DomotalkSocket(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val callbackIdCounter = AtomicInteger(0)

    private val _updates = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val updates: SharedFlow<JsonObject> = _updates.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile var token: String? = null

    suspend fun connect(
        host: String,
        secure: Boolean,
        trustAllCertificates: Boolean = secure,
    ) {
        _connectionState.value = ConnectionState.CONNECTING

        try {
            val scheme = if (secure) "wss" else "ws"
            val url = "$scheme://$host$WS_PATH"

            val client =
                if (secure && trustAllCertificates) {
                    okHttpClient
                        .newBuilder()
                        .sslSocketFactory(TrustAllCerts.sslSocketFactory, TrustAllCerts.x509TrustManager)
                        .hostnameVerifier(TrustAllCerts.hostnameVerifier)
                        .build()
                } else {
                    okHttpClient
                }

            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Sec-WebSocket-Protocol", "$SUBPROTOCOL_DOMOTALK, $SUBPROTOCOL_PING_PONG")
                    .build()

            suspendCancellableCoroutine<Unit> { continuation ->
                val listener =
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            _connectionState.value = ConnectionState.CONNECTED
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            handleMessage(text)
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            if (continuation.isActive) {
                                continuation.resumeWithException(DomotalkException.ConnectFailed(t))
                            } else {
                                failAllPending(DomotalkException.ConnectionLost(t))
                            }
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            failAllPending(DomotalkException.ConnectionLost())
                        }
                    }
                webSocket = client.newWebSocket(request, listener)
                continuation.invokeOnCancellation { webSocket?.cancel() }
            }
        } catch (e: DomotalkException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw e
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw DomotalkException.ConnectFailed(t)
        }
    }

    fun disconnect() {
        webSocket?.close(NORMAL_CLOSURE_CODE, "client_disconnect")
        webSocket = null
        token = null
        _connectionState.value = ConnectionState.DISCONNECTED
        failAllPending(DomotalkException.ConnectionLost())
    }

    /**
     * Sends one `domotalk` request and suspends until the reply with the same `callback_id`
     * arrives (or [timeoutMillis] elapses). Returns the unwrapped `payload` element — see
     * [extractPayload] for why that needs its own handling.
     */
    suspend fun request(
        verb: String,
        subject: String,
        options: JsonObject? = null,
        filters: JsonArray? = null,
        timeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): JsonElement {
        val socket = webSocket ?: throw DomotalkException.NotConnected()
        val callbackId = nextCallbackId()
        val deferred = CompletableDeferred<JsonObject>()
        pending[callbackId] = deferred

        val payload =
            buildJsonObject {
                put("verb", JsonPrimitive(verb))
                put("subject", JsonPrimitive(subject))
                options?.let { put("options", it) }
                filters?.let { put("filters", it) }
                token?.let { put("token", JsonPrimitive(it)) }
                put("callback_id", JsonPrimitive(callbackId))
            }

        val sent = socket.send(json.encodeToString(JsonObject.serializer(), payload))
        if (!sent) {
            pending.remove(callbackId)
            throw DomotalkException.NotConnected()
        }

        return try {
            withTimeout(timeoutMillis) { deferred.await() }.let(::extractPayload)
        } catch (e: TimeoutCancellationException) {
            throw DomotalkException.RequestTimeout(verb, subject)
        } finally {
            pending.remove(callbackId)
        }
    }

    private fun handleMessage(text: String) {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
        val obj = element as? JsonObject ?: return

        val callbackId = obj["callback_id"]?.jsonPrimitive?.content?.toIntOrNull()
        val deferred = callbackId?.let { pending[it] }
        if (deferred != null) {
            deferred.complete(obj)
        } else {
            if (!_updates.tryEmit(obj)) {
                Timber.w("Dropped a domotalk push update: buffer full")
            }
        }
    }

    /**
     * `payload` is sometimes a JSON-encoded string that must be re-parsed, and sometimes the
     * object/array directly (see protocol notes §2.2). Handle both.
     */
    private fun extractPayload(response: JsonObject): JsonElement {
        val payload = response["payload"] ?: return response
        val primitive = payload as? JsonPrimitive
        return if (primitive != null && primitive.isString) {
            runCatching { json.parseToJsonElement(primitive.content) }
                .getOrElse { throw DomotalkException.MalformedResponse("payload was not valid JSON") }
        } else {
            payload
        }
    }

    private fun failAllPending(exception: DomotalkException) {
        pending.values.forEach { it.completeExceptionally(exception) }
        pending.clear()
    }

    private fun nextCallbackId(): Int = callbackIdCounter.getAndUpdate { (it + 1) % CALLBACK_ID_WRAP }

    companion object {
        const val WS_PATH = "/ws/"
        const val SUBPROTOCOL_DOMOTALK = "domotalk"
        const val SUBPROTOCOL_PING_PONG = "ping-pong"
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 10_000L
        const val CALLBACK_ID_WRAP = 10_000
        const val NORMAL_CLOSURE_CODE = 1000

        fun defaultOkHttpClient(
            pingIntervalMillis: Long = 2000L,
            connectTimeoutMillis: Long = 15_000L,
        ): OkHttpClient =
            OkHttpClient
                .Builder()
                .pingInterval(pingIntervalMillis, TimeUnit.MILLISECONDS)
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .build()
    }
}
