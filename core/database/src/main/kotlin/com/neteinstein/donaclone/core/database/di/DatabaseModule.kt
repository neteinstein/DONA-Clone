package com.neteinstein.donaclone.core.database.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.neteinstein.donaclone.core.database.DonaDatabase
import com.neteinstein.donaclone.core.database.prefs.ActionConfirmationPreferences
import com.neteinstein.donaclone.core.database.prefs.BiometricPreferences
import com.neteinstein.donaclone.core.database.prefs.DebugPreferences
import com.neteinstein.donaclone.core.database.prefs.RoomsDisplayPreferences
import com.neteinstein.donaclone.core.database.prefs.SessionPreferences
import com.neteinstein.donaclone.core.database.prefs.ThemePreferences
import com.neteinstein.donaclone.core.database.security.CredentialCipher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

val databaseModule =
    module {
        single {
            Room
                .databaseBuilder(androidContext(), DonaDatabase::class.java, DonaDatabase.DATABASE_NAME)
                .addMigrations(DonaDatabase.MIGRATION_1_2)
                .build()
        }
        single { get<DonaDatabase>().houseDao() }
        single { SessionPreferences(androidContext().sessionDataStore) }
        single { ThemePreferences(androidContext().sessionDataStore) }
        single { BiometricPreferences(androidContext().sessionDataStore) }
        single { DebugPreferences(androidContext().sessionDataStore) }
        single { ActionConfirmationPreferences(androidContext().sessionDataStore) }
        single { RoomsDisplayPreferences(androidContext().sessionDataStore) }
        single { CredentialCipher() }
    }
