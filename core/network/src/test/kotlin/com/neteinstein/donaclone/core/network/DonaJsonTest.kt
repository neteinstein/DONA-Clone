package com.neteinstein.donaclone.core.network

import com.neteinstein.donaclone.core.network.dto.ActionDto
import com.neteinstein.donaclone.core.network.dto.TriggerDto
import com.neteinstein.donaclone.core.network.dto.UserDto
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonaJsonTest {
    private val json = donaJson()

    @Test
    fun `a user update carries fields that happen to equal their DTO default`() {
        // Regression: with kotlinx's default `encodeDefaults = false`, `enabled`/`remoteAccessible`
        // were dropped whenever they were being set to `true`, so enabling a disabled user (or
        // granting remote access) went to the hub as a no-op and Save looked like it did nothing.
        val encoded =
            json.encodeToJsonElement(
                UserDto.serializer(),
                UserDto(id = 36171, role = 4, name = "GVP", enabled = true, remoteAccessible = true, hidden = false),
            )

        val fields = encoded as kotlinx.serialization.json.JsonObject
        assertEquals(true, fields["enabled"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, fields["remoteAccessible"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(false, fields["hidden"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `a null stays omitted so create requests still leave out the id the hub assigns`() {
        val encoded =
            json.encodeToJsonElement(TriggerDto.serializer(), TriggerDto(type = 1, time = "07:20")) as
                kotlinx.serialization.json.JsonObject

        assertFalse("id", encoded.containsKey("id"))
        assertFalse("triggerer", encoded.containsKey("triggerer"))
        assertTrue("type", encoded.containsKey("type"))
        assertTrue("time", encoded.containsKey("time"))
    }

    @Test
    fun `an action's chain timing is always sent, even at its defaults`() {
        val encoded =
            json.encodeToJsonElement(
                ActionDto.serializer(),
                ActionDto(type = 0, device = 280, action = 1, withLast = false, delayFromLast = 0),
            ) as kotlinx.serialization.json.JsonObject

        assertTrue("withLast", encoded.containsKey("withLast"))
        assertTrue("delayFromLast", encoded.containsKey("delayFromLast"))
        assertFalse("nextAction", encoded.containsKey("nextAction"))
    }
}
