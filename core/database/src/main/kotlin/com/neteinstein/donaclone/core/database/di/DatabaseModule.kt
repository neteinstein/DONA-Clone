package com.neteinstein.donaclone.core.database.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.neteinstein.donaclone.core.database.DonaDatabase
import com.neteinstein.donaclone.core.database.prefs.SessionPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

val databaseModule =
    module {
        single {
            Room.databaseBuilder(androidContext(), DonaDatabase::class.java, DonaDatabase.DATABASE_NAME).build()
        }
        single { get<DonaDatabase>().houseDao() }
        single { SessionPreferences(androidContext().sessionDataStore) }
    }
