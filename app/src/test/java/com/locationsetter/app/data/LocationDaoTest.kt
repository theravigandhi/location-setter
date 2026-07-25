package com.locationsetter.app.data

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.locationsetter.app.data.room.AppDatabase
import com.locationsetter.app.data.room.LocationDao
import com.locationsetter.app.data.room.LocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: LocationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.locationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadBack() = runTest {
        val id = dao.insert(LocationEntity(name = "Home", latitude = 37.42, longitude = -122.08))
        val loaded = dao.getById(id)
        assertEquals("Home", loaded?.name)
    }

    @Test
    fun updateRenamesLocation() = runTest {
        val id = dao.insert(LocationEntity(name = "Old", latitude = 1.0, longitude = 2.0))
        val location = dao.getById(id)!!
        dao.update(location.copy(name = "New"))
        assertEquals("New", dao.getById(id)?.name)
    }

    @Test
    fun deleteRemovesLocation() = runTest {
        val id = dao.insert(LocationEntity(name = "Temp", latitude = 0.0, longitude = 0.0))
        val location = dao.getById(id)!!
        dao.delete(location)
        assertNull(dao.getById(id))
    }

    @Test
    fun getAllOrdersByNewestFirst() = runTest {
        dao.insert(LocationEntity(name = "First", latitude = 0.0, longitude = 0.0, createdAt = 1000))
        dao.insert(LocationEntity(name = "Second", latitude = 0.0, longitude = 0.0, createdAt = 2000))
        val all = dao.getAll().first()
        assertTrue(all.first().name == "Second")
    }
}
