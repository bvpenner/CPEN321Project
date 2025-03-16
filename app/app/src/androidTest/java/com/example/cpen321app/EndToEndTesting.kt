package com.example.cpen321app

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.junit.runner.Description as JUnitDescription

/**
 * End-to-end UI tests for basic task management functionality
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndTesting : BaseUITest() {

    private val idlingResource = TaskViewModel.IdlingResourceManager.countingIdlingResource

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val idlingResourceRule = object : TestWatcher() {
        override fun starting(description: JUnitDescription) {
            IdlingRegistry.getInstance().register(idlingResource)
        }

        override fun finished(description: JUnitDescription) {
            if (IdlingRegistry.getInstance().resources.contains(idlingResource)) {
                IdlingRegistry.getInstance().unregister(idlingResource)
            }
        }
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.cpen321app", appContext.packageName)
    }

    @Test
    fun testManageTaskSuccess() {
        login()
        navigateToTaskList()
        val taskName = getRandomTestTaskName()

        addTask(taskName, "test description", "1", "60")
        createdTestTasks.add(taskName) // Track for cleanup

        verifyTaskExists(taskName)

        deleteTask(taskName)
        verifyTaskDeleted(taskName)
        createdTestTasks.remove(taskName) // No longer needs cleanup
    }

    @Test
    fun testCreateTask_InputInvalidLat() {
        login()
        navigateToTaskList()

        val invalidAddTaskParams = TaskInputParams(
            name = getRandomTestTaskName(),
            description = "test description",
            priority = "1",
            latitude = "100.0",  // Invalid latitude
            longitude = "50.0"
        )

        beginAddTask()
        fillTaskForm(invalidAddTaskParams)
        createTask()

        onView(withText("Valid Latitude Required: Between -90 and 90 degrees"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCreateTask_InputInvalidLng() {
        login()
        navigateToTaskList()

        val invalidAddTaskParams = TaskInputParams(
            name = getRandomTestTaskName(),
            description = "test description",
            priority = "1",
            latitude = "40.0",
            longitude = "300.0"  // Invalid longitude
        )

        beginAddTask()
        fillTaskForm(invalidAddTaskParams)
        createTask()

        onView(withText("Valid Longitude Required: Between -180 and 180 degrees"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCreateTask_OtherInvalidInput() {
        login()
        navigateToTaskList()
        beginAddTask()

        // Test invalid name
        val invalidNameParams = TaskInputParams(
            name = " ",  // Invalid name
            description = "test description",
            priority = "1",
            latitude = "40.0",
            longitude = "50.0",
            duration = "60"
        )
        fillTaskForm(invalidNameParams)
        createTask()
        onView(withText("Valid name required"))
            .check(matches(isDisplayed()))

        // Test invalid duration
        val invalidDurationParams = invalidNameParams.copy(
            name = "ValidName",
            duration = "0"  // Invalid duration
        )
        onView(withId(R.id.editTextName)).perform(replaceText(invalidDurationParams.name), closeSoftKeyboard())
        onView(withId(R.id.editText_duration)).perform(replaceText(invalidDurationParams.duration), closeSoftKeyboard())
        createTask()
        onView(withText("Duration must be greater than 0."))
            .check(matches(isDisplayed()))

        // Test invalid priority
        val invalidPrioParams = invalidDurationParams.copy(
            duration = "60",
            priority = "4"  // Invalid priority
        )
        onView(withId(R.id.editText_duration)).perform(replaceText(invalidPrioParams.duration), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(replaceText(invalidPrioParams.priority), closeSoftKeyboard())
        createTask()
        onView(withText("Priority must be 1-3."))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testRouteOptimization() {
        login()
        navigateToTaskList()

        // Add multiple tasks at different locations
        val task1 = getRandomTestTaskName()
        val task2 = getRandomTestTaskName()

        // Add tasks with specified coordinates
        addTaskWithCoordinates(task1, "test description 1", "1", "60", "49.2600", "-123.1400")
        addTaskWithCoordinates(task2, "test description 2", "2", "30", "49.2800", "-123.1200")

        createdTestTasks.add(task1)
        createdTestTasks.add(task2)

        // Verify both tasks exist
        verifyTaskExists(task1)
        verifyTaskExists(task2)

        // Select the tasks for routing (if your app requires selection)
        selectTaskForRouting(task1)
        selectTaskForRouting(task2)

        // Trigger route optimization using the Plan Route button
        onView(withId(R.id.buttonPlanRoute)).perform(click())

        // Wait for notification to appear
        device.wait(Until.hasObject(By.text("New Route Available")), TEST_TIMEOUT)

        // Verify notification is shown
        val notificationManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull("Notification manager should not be null", notificationManager)
        assertTrue("Route notification should be active",
            notificationManager.activeNotifications.isNotEmpty())
    }
}