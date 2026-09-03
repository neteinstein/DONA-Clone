package com.neteinstein.donaclone.core.data.mapper

import com.neteinstein.donaclone.core.database.house.HouseEntity
import com.neteinstein.donaclone.core.database.security.CredentialCipher
import com.neteinstein.donaclone.core.database.security.EncryptedValue
import com.neteinstein.donaclone.core.model.House
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class HouseMapperTest {
    private val cipher = mockk<CredentialCipher>()
    private val mapper = HouseMapper(cipher)

    @Test
    fun `toEntity encrypts the password and never stores it in plaintext`() {
        val house = House(name = "Home", username = "alice", password = "hunter2")
        every { cipher.encrypt("hunter2") } returns EncryptedValue("cipher-text", "iv-value")

        val entity = mapper.toEntity(house)

        assertEquals("cipher-text", entity.passwordCipherText)
        assertEquals("iv-value", entity.passwordIv)
    }

    @Test
    fun `toDomain decrypts the stored password back to plaintext`() {
        val entity =
            HouseEntity(
                name = "Home",
                dns = null,
                secureDns = true,
                localIp = "192.168.1.50",
                secureLocalIp = false,
                username = "alice",
                passwordCipherText = "cipher-text",
                passwordIv = "iv-value",
                stayConnected = true,
                notificationId = null,
                codeOnDisarmAlarm = false,
            )
        every { cipher.decrypt(EncryptedValue("cipher-text", "iv-value")) } returns "hunter2"

        val house = mapper.toDomain(entity)

        assertEquals("hunter2", house.password)
        assertEquals("alice", house.username)
    }
}
