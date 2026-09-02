package com.neteinstein.donaclone.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neteinstein.donaclone.core.database.house.HouseDao
import com.neteinstein.donaclone.core.database.house.HouseEntity

@Database(entities = [HouseEntity::class], version = 1, exportSchema = true)
abstract class DonaDatabase : RoomDatabase() {
    abstract fun houseDao(): HouseDao

    companion object {
        const val DATABASE_NAME = "dona_clone.db"
    }
}
