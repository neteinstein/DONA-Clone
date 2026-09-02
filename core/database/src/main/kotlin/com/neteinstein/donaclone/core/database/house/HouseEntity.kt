package com.neteinstein.donaclone.core.database.house

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the original app's `Home` Room table 1:1 (see protocol notes §3.1) so multi-house
 * connection profiles behave the same way: [name] is the primary key/label, [dns] is an
 * optional cloud/DDNS address tried first, [localIp] is the LAN fallback, and the two
 * `secure*` flags pick `wss`/`ws` independently for each.
 */
@Entity(tableName = "houses")
data class HouseEntity(
    @PrimaryKey val name: String,
    val dns: String?,
    val secureDns: Boolean,
    val localIp: String?,
    val secureLocalIp: Boolean,
    val username: String,
    val password: String,
    val stayConnected: Boolean,
    val notificationId: String?,
    val codeOnDisarmAlarm: Boolean,
)
