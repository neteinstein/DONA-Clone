package com.neteinstein.donaclone.core.data.mapper

import com.neteinstein.donaclone.core.database.house.HouseEntity
import com.neteinstein.donaclone.core.database.security.CredentialCipher
import com.neteinstein.donaclone.core.database.security.EncryptedValue
import com.neteinstein.donaclone.core.model.House

/**
 * Encrypts/decrypts [House.password] exactly at the Room boundary, via [CredentialCipher] —
 * everything above [core.data] (domain use cases, ViewModels) only ever sees the plain, in-memory
 * [House.password]; [HouseEntity] only ever holds the Keystore-encrypted form.
 */
class HouseMapper(
    private val cipher: CredentialCipher,
) {
    fun toDomain(entity: HouseEntity): House =
        House(
            name = entity.name,
            dns = entity.dns,
            secureDns = entity.secureDns,
            localIp = entity.localIp,
            secureLocalIp = entity.secureLocalIp,
            username = entity.username,
            password = cipher.decrypt(EncryptedValue(entity.passwordCipherText, entity.passwordIv)),
            stayConnected = entity.stayConnected,
            notificationId = entity.notificationId,
            codeOnDisarmAlarm = entity.codeOnDisarmAlarm,
        )

    fun toEntity(house: House): HouseEntity {
        val encrypted = cipher.encrypt(house.password)
        return HouseEntity(
            name = house.name,
            dns = house.dns,
            secureDns = house.secureDns,
            localIp = house.localIp,
            secureLocalIp = house.secureLocalIp,
            username = house.username,
            passwordCipherText = encrypted.cipherTextBase64,
            passwordIv = encrypted.ivBase64,
            stayConnected = house.stayConnected,
            notificationId = house.notificationId,
            codeOnDisarmAlarm = house.codeOnDisarmAlarm,
        )
    }
}
