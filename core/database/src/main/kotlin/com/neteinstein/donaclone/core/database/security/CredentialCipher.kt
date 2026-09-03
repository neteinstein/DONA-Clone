package com.neteinstein.donaclone.core.database.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedValue(
    val cipherTextBase64: String,
    val ivBase64: String,
)

/**
 * Encrypts/decrypts small secrets (the [com.neteinstein.donaclone.core.database.house.HouseEntity]
 * password columns) with an AES/GCM key held in the Android Keystore, so a saved house's password
 * is no longer plaintext at rest. Deliberately *not* gated behind biometric authentication per use
 * (`setUserAuthenticationRequired(false)`) — the silent background session-refresh loop
 * (`AuthRepositoryImpl`) must be able to decrypt without prompting the user every time; the
 * fingerprint prompt is a separate, additional UX gate in front of *using* the app, not the
 * decryption key itself.
 */
class CredentialCipher {
    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    fun encrypt(plaintext: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedValue(
            cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    fun decrypt(value: EncryptedValue): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(value.ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(Base64.decode(value.cipherTextBase64, Base64.NO_WRAP))
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dona_credential_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
