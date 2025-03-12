package com.example.cpen321app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class RouteWorkerTest {

    private lateinit var context: Context
    private lateinit var worker: RouteWorker
    private lateinit var mockOkHttpClient: OkHttpClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MockitoAnnotations.initMocks(this)
        mockOkHttpClient = mock(OkHttpClient::class.java)
        worker = TestListenableWorkerBuilder<RouteWorker>(context).build()
        // Inject our mocked OkHttpClient and (optionally) fused location client via TestUtils.
        TestUtils.injectWorkerMocks(worker, null, mockOkHttpClient)
        // Setup location and HTTP responses (TestUtils should be updated accordingly)
        TestUtils.setupLocationMockResponse(worker)
        TestUtils.setupHttpMockResponse(worker, mock(okhttp3.Response::class.java))
    }

    @Test
    fun doWork_withValidLocationAndTasks_returnsSuccess() = runBlocking {
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockOkHttpClient).newCall(any())
    }

    @Test
    fun doWork_withoutLocationPermission_returnsFailure() = runBlocking {
        TestUtils.setupNoLocationPermission(worker)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_whenLocationUnavailable_returnsRetry() = runBlocking {
        TestUtils.setupLocationUnavailable(worker)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_whenHttpRequestFails_returnsFailure() = runBlocking {
        TestUtils.setupHttpRequestFailure(worker)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun orderTasksByNearestNeighbor_optimizesRouteCorrectly() {
        val tasks = listOf(
            Task("1", "Task 1", "10:00", "11:00", 1.0, 49.2827, -123.1207, Task.PRIORITY_HIGH, "Desc"),
            Task("2", "Task 2", "14:00", "15:00", 1.0, 49.2780, -123.1200, Task.PRIORITY_MEDIUM, "Desc"),
            Task("3", "Task 3", "12:00", "13:00", 1.0, 49.2600, -123.1400, Task.PRIORITY_LOW, "Desc")
        )
        val method: Method = RouteWorker::class.java.getDeclaredMethod(
            "orderTasksByNearestNeighbor",
            Double::class.java,
            Double::class.java,
            List::class.java
        )
        method.isAccessible = true
        val result = method.invoke(worker, 49.2606, -123.2460, tasks) as List<Task>
        // For this test, we expect Task "3" (the lowest priority value, i.e. the nearest) to come first,
        // followed by Task "2" then Task "1".
        assertEquals("3", result[0].id)
        assertEquals("2", result[1].id)
        assertEquals("1", result[2].id)
    }

    @Test
    fun buildGoogleMapsUrl_generatesCorrectUrl() {
        val tasks = listOf(
            Task("1", "Task 1", "10:00", "11:00", 1.0, 49.2827, -123.1207, Task.PRIORITY_HIGH, "Desc"),
            Task("2", "Task 2", "14:00", "15:00", 1.0, 49.2780, -123.1200, Task.PRIORITY_MEDIUM, "Desc")
        )
        val method: Method = RouteWorker::class.java.getDeclaredMethod(
            "buildGoogleMapsUrl",
            Double::class.java,
            Double::class.java,
            List::class.java
        )
        method.isAccessible = true
        val url = method.invoke(worker, 49.2606, -123.2460, tasks) as String
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
        // Check that the origin and destination portions are included (adjust decimals as needed)
        assertTrue(url.contains("&origin=49.2606,-123.246"))
        assertTrue(url.contains("&destination=49.2780,-123.12"))
    }
}
