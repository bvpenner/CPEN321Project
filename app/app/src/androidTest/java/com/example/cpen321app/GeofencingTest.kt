package com.example.cpen321app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Geofencing-specific tests that verify the geofence creation, visualization, and interaction
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GeofenceTest : BaseUITest() {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // Test coordinates (Vancouver area)
    private val testPolygon = listOf(
        LatLng(49.28, -123.12),  // UBC area coordinates
        LatLng(49.29, -123.11),
        LatLng(49.27, -123.10),
        LatLng(49.26, -123.13)
    )

    private val pointInside = LatLng(49.275, -123.115)  // Point inside the polygon
    private val pointOutside = LatLng(49.25, -123.08)   // Point outside the polygon

    @Before
    override fun setup() {
        super.setup()
        // Make sure we're logged in
        login()
    }

    /**
     * Tests that geofences are visualized on the map when adding a task
     */
    @Test
    fun testGeofenceVisualization() {
        // Create a task at a specific location
        val taskName = getRandomTestTaskName()
        navigateToTaskList()

        // Add task with coordinates
        addTaskWithCoordinates(
            LimitedTask(
                taskName,
                "Geofence Test Description",
                "1",
                "30",
                "49.2600",
                "-123.1400"
            )
        )
        createdTestTasks.add(taskName)

        // Verify task was created
        verifyTaskExists(taskName)

        // Enable geofencing for this task by toggling the switch
        enableGeofenceForTask(taskName)

        // Verify the geofence is enabled
        verifyGeofenceEnabled(taskName)

        // Navigate to map view to see the geofence
        navigateToMapView()

        // Wait for map to load and geofence to be drawn
        device.wait(Until.hasObject(By.res("com.example.cpen321app:id/map")), TEST_TIMEOUT)
        SystemClock.sleep(5000) // Give time for geofence to be fetched and drawn

        // Verify map is displayed
        onView(withId(R.id.map)).check(matches(isDisplayed()))
    }

    /**
     * Tests the integration between route optimization and geofencing
     */
    @Test
    fun testRouteOptimizationWithGeofencing() {
        // Create two tasks at different locations
        val task1 = getRandomTestTaskName()
        val task2 = getRandomTestTaskName()

        navigateToTaskList()

        // Add tasks with coordinates
        addTaskWithCoordinates(LimitedTask(task1, "Geofence Route Test 1", "1", "30", "49.2600", "-123.1400"))
        addTaskWithCoordinates(LimitedTask(task2, "Geofence Route Test 2", "2", "45", "49.2800", "-123.1200"))

        createdTestTasks.add(task1)
        createdTestTasks.add(task2)

        // Verify tasks exist
        verifyTaskExists(task1)
        verifyTaskExists(task2)

        // Enable geofencing for both tasks
        enableGeofenceForTask(task1)
        enableGeofenceForTask(task2)

        // Verify geofences are enabled
        verifyGeofenceEnabled(task1)
        verifyGeofenceEnabled(task2)

        // Select tasks for route planning
        selectTaskForRouting(task1)
        selectTaskForRouting(task2)

        // Trigger route planning - no scrollTo because it's not in a scrollable container
        onView(withId(R.id.buttonPlanRoute)).perform(click())

        // Wait for route notification to appear
        device.wait(Until.hasObject(By.text("New Route Available")), TEST_TIMEOUT)

        // Verify notification is shown
        val notificationManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull("Notification manager should not be null", notificationManager)
        assertTrue("Route notification should be active",
            notificationManager.activeNotifications.isNotEmpty())

        // Navigate to map to verify both tasks have geofences
        navigateToMapView()
        device.wait(Until.hasObject(By.res("com.example.cpen321app:id/map")), TEST_TIMEOUT)
        SystemClock.sleep(5000) // Give time for geofences to be fetched and drawn
    }

    /**
     * Tests PolyUtil's point-in-polygon detection (foundational to our geofencing)
     * This is a unit test that doesn't require UI interaction
     */
    @Test
    fun testPolyUtilContainsLocation() {
        // Test the core functionality from Google Maps Utils
        val insideResult = PolyUtil.containsLocation(pointInside, testPolygon, true)
        assertTrue("PolyUtil should detect point inside polygon", insideResult)

        val outsideResult = PolyUtil.containsLocation(pointOutside, testPolygon, true)
        assertFalse("PolyUtil should detect point outside polygon", outsideResult)
    }

    /**
     * Tests toggling geofence switches for multiple tasks
     */
    @Test
    fun testGeofenceSwitchToggle() {
        // Create tasks
        val task1 = getRandomTestTaskName()
        val task2 = getRandomTestTaskName()

        navigateToTaskList()

        // Add tasks
        addTaskWithCoordinates(LimitedTask(task1, "Geofence Toggle Test 1", "1", "30", "49.2600", "-123.1400"))
        addTaskWithCoordinates(LimitedTask(task2, "Geofence Toggle Test 2", "2", "45", "49.2800", "-123.1200"))

        createdTestTasks.add(task1)
        createdTestTasks.add(task2)

        // Verify tasks exist
        verifyTaskExists(task1)
        verifyTaskExists(task2)

        // Enable geofencing for first task only
        enableGeofenceForTask(task1)

        // Navigate to map view to verify first task has geofence
        navigateToMapView()
        device.wait(Until.hasObject(By.res("com.example.cpen321app:id/map")), TEST_TIMEOUT)
        SystemClock.sleep(3000)

        // Go back to list and enable second task
        navigateToTaskList()
        enableGeofenceForTask(task2)

        // Verify both tasks have geofencing enabled
        verifyGeofenceEnabled(task1)
        verifyGeofenceEnabled(task2)

        // Navigate to map to verify both tasks have geofences
        navigateToMapView()
        device.wait(Until.hasObject(By.res("com.example.cpen321app:id/map")), TEST_TIMEOUT)
        SystemClock.sleep(3000)
    }
}