package com.neteinstein.donaclone.core.data.auth

import java.security.MessageDigest

/**
 * The DPU only ever sees an MD5 hex digest of the password, unsalted (`p4.h.j`, protocol
 * notes §2.3) — this is a real, weak property of the hub's own protocol, not a choice this
 * client is making; we replicate it purely for wire compatibility with existing hardware.
 */
object PasswordHasher {
    fun md5Hex(password: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
