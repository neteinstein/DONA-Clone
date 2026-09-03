package com.neteinstein.donaclone.core.database.house

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.neteinstein.donaclone.core.database.DonaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HouseDaoTest {
    private lateinit var database: DonaDatabase
    private lateinit var dao: HouseDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.houseDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun sampleHouse(name: String = "My House") =
        HouseEntity(
            name = name,
            dns = "myhouse.example.com",
            secureDns = true,
            localIp = "192.168.1.50",
            secureLocalIp = false,
            username = "alice",
            passwordCipherText = "cipher-text",
            passwordIv = "iv-value",
            stayConnected = true,
            notificationId = null,
            codeOnDisarmAlarm = false,
        )

    @Test
    fun `upsert then findByName returns the stored house`() =
        runBlocking {
            dao.upsert(sampleHouse())

            val found = dao.findByName("My House")

            assertEquals("myhouse.example.com", found?.dns)
            assertEquals("192.168.1.50", found?.localIp)
        }

    @Test
    fun `upsert replaces an existing house with the same name`() =
        runBlocking {
            dao.upsert(sampleHouse())
            dao.upsert(sampleHouse().copy(localIp = "192.168.1.99"))

            val found = dao.findByName("My House")

            assertEquals("192.168.1.99", found?.localIp)
            assertEquals(1, dao.observeAll().first().size)
        }

    @Test
    fun `deleteByName removes the house`() =
        runBlocking {
            dao.upsert(sampleHouse())

            dao.deleteByName("My House")

            assertNull(dao.findByName("My House"))
        }
}
