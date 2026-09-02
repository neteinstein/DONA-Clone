package com.neteinstein.donaclone.core.network.discovery

import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.HubType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Replicates the original app's LAN discovery: broadcast the ASCII string "domobroadcast" to
 * 255.255.255.255 on UDP ports 7777 and 7778 in parallel, listen for ~2s, and parse any reply
 * that isn't an echo of our own 13-byte probe (or a 32-byte provisioning-ack packet) as a JSON
 * device announcement. See protocol notes: discovery §1.
 */
interface DiscoveryClient {
    /** Emits every distinct (by MAC) hub found within [listenWindowMillis] of starting the scan. */
    fun discoverHouses(listenWindowMillis: Long = DEFAULT_LISTEN_WINDOW_MILLIS): Flow<DiscoveredHouse>

    companion object {
        const val DEFAULT_LISTEN_WINDOW_MILLIS = 2000L
    }
}

class UdpDiscoveryClient : DiscoveryClient {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    override fun discoverHouses(listenWindowMillis: Long): Flow<DiscoveredHouse> = callbackFlow {
        val seenMacs = HashSet<String>()
        val sockets = DISCOVERY_PORTS.mapNotNull { port -> runCatching { openBroadcastSocket(port) }.getOrNull() }

        if (sockets.isEmpty()) {
            close(IllegalStateException("Could not open any discovery UDP socket"))
            return@callbackFlow
        }

        val listenerJobs = sockets.map { socket ->
            launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                socket.soTimeout = 250
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val bytes = packet.data.copyOfRange(0, packet.length)
                        parseReply(bytes)?.let { house ->
                            if (seenMacs.add(house.mac)) trySend(house)
                        }
                    } catch (_: SocketTimeoutException) {
                        // expected, keep polling until the window closes
                    } catch (_: SocketException) {
                        break
                    } catch (t: Throwable) {
                        Timber.w(t, "Discovery socket read failed")
                    }
                }
            }
        }

        sockets.forEach { socket ->
            runCatching { broadcastProbe(socket) }.onFailure { Timber.w(it, "Failed to send discovery probe") }
        }

        launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(listenWindowMillis)
            close()
        }

        awaitClose {
            listenerJobs.forEach { it.cancel() }
            sockets.forEach { runCatching { it.close() } }
        }
    }

    private fun openBroadcastSocket(port: Int): DatagramSocket =
        DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(port))
        }

    private fun broadcastProbe(socket: DatagramSocket) {
        val bytes = BROADCAST_MESSAGE.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(bytes, bytes.size, InetSocketAddress(BROADCAST_ADDRESS, socket.localPort))
        socket.send(packet)
    }

    private fun parseReply(bytes: ByteArray): DiscoveredHouse? {
        if (bytes.size == BROADCAST_MESSAGE.length || bytes.size == PROVISIONING_ACK_SIZE) return null
        return runCatching {
            val text = String(bytes, Charsets.UTF_8)
            val json = lenientJson.parseToJsonElement(text).jsonObject
            val mac = json["MAC"]?.jsonPrimitive?.content ?: return null
            val ip = json["IP"]?.jsonPrimitive?.content ?: return null
            val hardware = json["HW"]?.jsonPrimitive?.content
            val firmware = json["FW"]?.jsonPrimitive?.content
            val mtype = json["MTYPE"]?.jsonPrimitive?.content?.toIntOrNull()
            DiscoveredHouse(
                mac = mac,
                ip = ip,
                gateway = json["GW"]?.jsonPrimitive?.content,
                subnetMask = json["SM"]?.jsonPrimitive?.content,
                dhcp = json["DHCP"]?.jsonPrimitive?.content == "1",
                hubType = resolveHubType(mtype, firmware, hardware),
                serialNumber = json["SN"]?.jsonPrimitive?.content,
                hardwareVersion = hardware,
                firmwareVersion = firmware,
            )
        }.onFailure { Timber.w(it, "Ignoring unparsable discovery reply") }.getOrNull()
    }

    /** Mirrors `y4.b`'s firmware-gated MTYPE-vs-HW fallback for classifying the hub hardware. */
    private fun resolveHubType(mtype: Int?, firmware: String?, hardware: String?): HubType {
        val firmwareParts = firmware?.split(".")?.mapNotNull { it.toIntOrNull() }
        val firmwareAtLeast1_2 = firmwareParts != null &&
            firmwareParts.isNotEmpty() &&
            (firmwareParts[0] > 1 || (firmwareParts[0] == 1 && (firmwareParts.getOrNull(1) ?: 0) >= 2))

        if (firmwareAtLeast1_2 && mtype != null) {
            return when (mtype) {
                0 -> HubType.DPU
                1 -> HubType.D815
                2 -> HubType.D808
                3 -> HubType.WIFI_SHUTTER
                4 -> HubType.WIFI_LIGHT
                5 -> HubType.WIFI_OUTLET
                else -> HubType.UNKNOWN
            }
        }

        val hw = hardware.orEmpty()
        return when {
            hw.startsWith("0.") -> HubType.DPU
            hw.startsWith("1.") -> HubType.D815
            hw.startsWith("2.1") -> HubType.WIFI_SHUTTER
            hw.startsWith("2.2") -> HubType.WIFI_LIGHT
            hw.startsWith("2.3") -> HubType.WIFI_OUTLET
            else -> HubType.UNKNOWN
        }
    }

    companion object {
        const val BROADCAST_MESSAGE = "domobroadcast"
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val PROVISIONING_ACK_SIZE = 32
        val DISCOVERY_PORTS = listOf(7777, 7778)
    }
}
