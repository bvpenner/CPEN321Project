package com.example.cpen321app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Unit tests for RouteWorker functionality
 * These tests verify the routing algorithm and notification display
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RouteWorkerTest {

    private lateinit var context: Context
    private lateinit var executor: Executor

    // Test coordinates (Vancouver area)
    private val testLatitude = 49.2827
    private val testLongitude = -123.1207

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
    }
    
    private fun createTestWorker(): RouteWorker {
        return TestWorkerBuilder<RouteWorker>(context, executor)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? {
                    return RouteWorker(appContext, workerParameters)
                }
            })
            .build() as RouteWorker
    }

    private fun createTestTask(
        id: String,
        name: String,
        startTime: String,
        endTime: String,
        duration: Int,
        latitude: Double,
        longitude: Double,
        priority: Int,
        description: String
    ): Task {
        return Task(id, name, startTime, endTime, duration, latitude, longitude, priority, description)
    }

    @Test
    fun testRouteWorker_EmptyTaskList() {
        // Arrange
        val worker = createTestWorker()

        // Act
        val orderedTasks = worker.orderTasksByNearestNeighbor(testLatitude, testLongitude, emptyList())

        // Assert
        assertEquals("Empty task list should return empty result", 0, orderedTasks.size)
    }

    @Test
    fun testRouteWorker_SingleTask() {
        // Arrange
        val worker = createTestWorker()
        val singleTask = createTestTask(
            "1", "Single Task", "10:00", "11:00",
            60, 49.28, -123.12, 1, "Test description"
        )

        // Act
        val orderedTasks = worker.orderTasksByNearestNeighbor(
            testLatitude, testLongitude, listOf(singleTask)
        )

        // Assert
        assertEquals("Should return one task", 1, orderedTasks.size)
        assertEquals("Task should be the same as input", singleTask.id, orderedTasks[0].id)
    }

    @Test
    fun testRouteWorker_TaskOrdering() {
        // Arrange
        val worker = createTestWorker()

        // Create test tasks with known locations
        val tasks = listOf(
            createTestTask("1", "Task A", "10:00", "11:00", 60, 49.28, -123.12, 1, "Desc A"),
            createTestTask("2", "Task B", "10:30", "11:30", 60, 49.29, -123.13, 1, "Desc B"),
            createTestTask("3", "Task C", "11:00", "12:00", 60, 49.30, -123.14, 1, "Desc C")
        )

        // Act - Order tasks by nearest neighbor algorithm
        val orderedTasks = worker.orderTasksByNearestNeighbor(testLatitude, testLongitude, tasks)

        // Assert - Verify ordering
        assertEquals("Should return all tasks", 3, orderedTasks.size)
        assertEquals("First task should be closest to starting point", "Task A", orderedTasks[0].name)
        assertEquals("Second task should be next closest", "Task B", orderedTasks[1].name)
        assertEquals("Third task should be furthest", "Task C", orderedTasks[2].name)
    }

    @Test
    fun testRouteWorker_Ordering_DifferentStartPosition() {
        // Arrange
        val worker = createTestWorker()

        // Create test tasks
        val tasks = listOf(
            createTestTask("1", "Task A", "10:00", "11:00", 60, 49.28, -123.12, 1, "Desc A"),
            createTestTask("2", "Task B", "10:30", "11:30", 60, 49.29, -123.13, 1, "Desc B"),
            createTestTask("3", "Task C", "11:00", "12:00", 60, 49.30, -123.14, 1, "Desc C")
        )

        // Act - Order tasks from a different starting position
        val differentLat = 49.30
        val differentLng = -123.14
        val orderedTasks = worker.orderTasksByNearestNeighbor(differentLat, differentLng, tasks)

        // Assert - Verify different ordering based on different starting position
        assertEquals("Should return all tasks", 3, orderedTasks.size)
        assertEquals("First task should be closest to new starting point", "Task C", orderedTasks[0].name)
        assertEquals("Second task should be next closest to new starting point", "Task B", orderedTasks[1].name)
        assertEquals("Third task should be furthest from new starting point", "Task A", orderedTasks[2].name)
    }

    @Test
    fun testRouteWorker_ShuffledInput() {
        // Arrange
        val worker = createTestWorker()

        // Create test tasks in shuffled order
        val tasks = listOf(
            createTestTask("3", "Task C", "11:00", "12:00", 60, 49.30, -123.14, 1, "Desc C"),
            createTestTask("1", "Task A", "10:00", "11:00", 60, 49.28, -123.12, 1, "Desc A"),
            createTestTask("2", "Task B", "10:30", "11:30", 60, 49.29, -123.13, 1, "Desc B")
        )

        // Act
        val orderedTasks = worker.orderTasksByNearestNeighbor(testLatitude, testLongitude, tasks)

        // Assert - Order should be based on distance, not input order
        assertEquals("Should return ordered by distance, not input order", "Task A", orderedTasks[0].name)
        assertEquals("Task B", orderedTasks[1].name)
        assertEquals("Task C", orderedTasks[2].name)
    }

    @Test
    fun testRouteWorker_NotificationDisplayed() {
        // Arrange
        val worker = createTestWorker()
        val mapsUrl = "https://www.google.com/maps/dir/?api=1&origin=49.27,-123.11"

        // Act - Show route notification
        worker.showRouteNotification(context, mapsUrl)

        // Assert - Verify notification is displayed
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Check that notifications exist
        assertNotNull("Notification manager should not be null", notificationManager)
        assertTrue("At least one notification should be active",
            notificationManager.activeNotifications.isNotEmpty())
    }

    @Test
    fun testRouteWorker_BuildGoogleMapsUrl() {
        // Arrange
        val worker = createTestWorker()
        val tasks = listOf(
            createTestTask("1", "Task A", "10:00", "11:00", 60, 49.28, -123.12, 1, "Desc A"),
            createTestTask("2", "Task B", "10:30", "11:30", 60, 49.29, -123.13, 1, "Desc B")
        )

        // Act - Use reflection to access private method
        val url = worker.javaClass.getDeclaredMethod("buildGoogleMapsUrl",
            Double::class.java,
            Double::class.java,
            List::class.java)
        url.isAccessible = true
        val mapsUrl = url.invoke(worker, testLatitude, testLongitude, tasks) as String

        // Assert
        assertTrue("URL should contain origin coordinates",
            mapsUrl.contains("origin=$testLatitude,$testLongitude"))
        assertTrue("URL should contain destination coordinates",
            mapsUrl.contains("destination=49.29,-123.13"))
        assertTrue("URL should contain at least one waypoint",
            mapsUrl.contains("waypoints=49.28,-123.12"))
    }

    @Test
    fun testRouteWorker_DistanceCalculation() {
        // Arrange
        val worker = createTestWorker()

        // Get reference to the private computeDistance method using reflection
        val computeDistanceMethod = worker.javaClass.getDeclaredMethod(
            "computeDistance",
            Double::class.java,
            Double::class.java,
            Double::class.java,
            Double::class.java
        )
        computeDistanceMethod.isAccessible = true

        // Act - Calculate known distances
        val distance1 = computeDistanceMethod.invoke(
            worker,
            49.2827, -123.1207, // UBC
            49.2780, -123.0979  // Downtown Vancouver
        ) as Double

        val distance2 = computeDistanceMethod.invoke(
            worker,
            49.2827, -123.1207, // UBC
            49.2827, -123.1207  // Same point (UBC)
        ) as Double

        // Assert
        assertTrue("Distance between UBC and Downtown should be >0", distance1 > 0)
        assertEquals("Distance between same points should be 0", 0.0, distance2, 0.01)
    }
}