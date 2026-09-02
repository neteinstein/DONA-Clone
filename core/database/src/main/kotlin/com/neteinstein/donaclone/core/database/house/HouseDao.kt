package com.neteinstein.donaclone.core.database.house

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseDao {
    @Query("SELECT * FROM houses ORDER BY name ASC")
    fun observeAll(): Flow<List<HouseEntity>>

    @Query("SELECT * FROM houses WHERE name = :name")
    suspend fun findByName(name: String): HouseEntity?

    @Upsert
    suspend fun upsert(house: HouseEntity)

    @Delete
    suspend fun delete(house: HouseEntity)

    @Query("DELETE FROM houses WHERE name = :name")
    suspend fun deleteByName(name: String)
}
