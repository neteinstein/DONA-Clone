package com.neteinstein.donaclone.core.database.house

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the original app's `Home` Room table (see protocol notes §3.1) so multi-house
 * connection profiles behave the same way: [name] is the primary key/label, [dns] is an
 * optional cloud/DDNS address tried first, [localIp] is the LAN fallback, and the two
 * `secure*` flags pick `wss`/`ws` independently for each.
 *
 * Unlike the original app, [passwordCipherText]/[passwordIv] hold the password encrypted with an
 * Android-Keystore-backed AES/GCM key ([com.neteinstein.donaclone.core.database.security.CredentialCipher])
 * rather than plaintext — see [DonaDatabase.MIGRATION_1_2]. Encryption/decryption happens at the
 * `core:data` mapper boundary; every layer above it still deals in a plain `House.password`.
 */
@Entity(tableName = "houses")
data class HouseEntity(
    @PrimaryKey val name: String,
    val dns: String?,
    val secureDns: Boolean,
    val localIp: String?,
    val secureLocalIp: Boolean,
    val username: String,
    val passwordCipherText: String,
    val passwordIv: String,
    val stayConnected: Boolean,
    val notificationId: String?,
    val codeOnDisarmAlarm: Boolean,
)
