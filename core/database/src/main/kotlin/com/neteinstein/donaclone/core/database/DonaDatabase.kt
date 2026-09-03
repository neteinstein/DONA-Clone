package com.neteinstein.donaclone.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neteinstein.donaclone.core.database.house.HouseDao
import com.neteinstein.donaclone.core.database.house.HouseEntity
import com.neteinstein.donaclone.core.database.security.CredentialCipher

@Database(entities = [HouseEntity::class], version = 2, exportSchema = true)
abstract class DonaDatabase : RoomDatabase() {
    abstract fun houseDao(): HouseDao

    companion object {
        const val DATABASE_NAME = "dona_clone.db"

        /**
         * Encrypts every previously-plaintext `password` column with [CredentialCipher] (an
         * Android-Keystore-backed AES/GCM key), replacing it with the `passwordCipherText`/
         * `passwordIv` pair `HouseEntity` now stores instead — see that class's doc comment.
         * Rebuilds the table (copy-into-a-new-table-then-rename) rather than `ALTER TABLE ...
         * DROP COLUMN`, which isn't supported by the SQLite version bundled with older Android
         * releases this app still supports. Deliberately not
         * `fallbackToDestructiveMigration()`, which would silently drop every saved house.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE houses_new (
                            name TEXT NOT NULL PRIMARY KEY,
                            dns TEXT,
                            secureDns INTEGER NOT NULL,
                            localIp TEXT,
                            secureLocalIp INTEGER NOT NULL,
                            username TEXT NOT NULL,
                            passwordCipherText TEXT NOT NULL,
                            passwordIv TEXT NOT NULL,
                            stayConnected INTEGER NOT NULL,
                            notificationId TEXT,
                            codeOnDisarmAlarm INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    val cipher = CredentialCipher()
                    db.query("SELECT * FROM houses").use { cursor ->
                        val nameIdx = cursor.getColumnIndexOrThrow("name")
                        val dnsIdx = cursor.getColumnIndexOrThrow("dns")
                        val secureDnsIdx = cursor.getColumnIndexOrThrow("secureDns")
                        val localIpIdx = cursor.getColumnIndexOrThrow("localIp")
                        val secureLocalIpIdx = cursor.getColumnIndexOrThrow("secureLocalIp")
                        val usernameIdx = cursor.getColumnIndexOrThrow("username")
                        val passwordIdx = cursor.getColumnIndexOrThrow("password")
                        val stayConnectedIdx = cursor.getColumnIndexOrThrow("stayConnected")
                        val notificationIdIdx = cursor.getColumnIndexOrThrow("notificationId")
                        val codeOnDisarmAlarmIdx = cursor.getColumnIndexOrThrow("codeOnDisarmAlarm")

                        while (cursor.moveToNext()) {
                            val encrypted = cipher.encrypt(cursor.getString(passwordIdx))
                            db.execSQL(
                                """
                                INSERT INTO houses_new
                                    (name, dns, secureDns, localIp, secureLocalIp, username,
                                     passwordCipherText, passwordIv, stayConnected, notificationId,
                                     codeOnDisarmAlarm)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """.trimIndent(),
                                arrayOf(
                                    cursor.getString(nameIdx),
                                    cursor.getString(dnsIdx),
                                    cursor.getInt(secureDnsIdx),
                                    cursor.getString(localIpIdx),
                                    cursor.getInt(secureLocalIpIdx),
                                    cursor.getString(usernameIdx),
                                    encrypted.cipherTextBase64,
                                    encrypted.ivBase64,
                                    cursor.getInt(stayConnectedIdx),
                                    cursor.getString(notificationIdIdx),
                                    cursor.getInt(codeOnDisarmAlarmIdx),
                                ),
                            )
                        }
                    }

                    db.execSQL("DROP TABLE houses")
                    db.execSQL("ALTER TABLE houses_new RENAME TO houses")
                }
            }
    }
}
