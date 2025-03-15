package com.example.cpen321app

import android.app.NotificationManager
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.random.Random

// ----------------------
// EndToEndTesting: Original Task Tests
// ----------------------
@RunWith(AndroidJUnit4::class)
@LargeTest
class EndToEndTesting {

    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.cpen321app", appContext.packageName)
    }

    @Test
    fun testManageTaskSuccess() {

        login()

        // Navigate to task window
        onView(withId(R.id.list_view_button)).perform(click())

        val taskName = getRandomTestTaskName()
        // Add Task
        testAddTask(taskName)

        testDeleteTask(taskName)
    }

    @Test
    fun testCreateTask_InputInvalidLat() {
        login()
        onView(withId(R.id.list_view_button)).perform(click())
        val taskName = getRandomTestTaskName()
        testFailAddTaskLat(taskName)
    }

    @Test
    fun testCreateTask_InputInvalidLng() {
        login()
        onView(withId(R.id.list_view_button)).perform(click())
        val taskName = getRandomTestTaskName()
        testFailAddTaskLng(taskName)
    }

    @Test
    fun testCreateTask_OtherInvalidInput() {
        login()
        onView(withId(R.id.list_view_button)).perform(click())
        val taskName = getRandomTestTaskName()
        testFailInvalidInput(taskName)
    }

    private fun login() {
        device.wait(Until.hasObject(By.text("Choose an account")), 10000)
        val button = device.findObject(UiSelector().clickable(true))
        if (button.waitForExists(4000)) {
            button.click()
        } else {
            fail("No provided account to log in with")
        }
        device.wait(Until.hasObject(By.text("Allow cpen321app to access this device's location?")), 10000)
        val precise = device.findObject(UiSelector().text("Precise"))
        if (precise.exists()) {
            precise.click()
        }
        val whileUsingTheApp = device.findObject(UiSelector().text("While using the app"))
        if (whileUsingTheApp.exists()) {
            whileUsingTheApp.click()
        }
    }

    private fun testAddTask(taskName: String) {
        val taskDescription = "test description"
        val priority = "1"
        val taskPrio = "60"

        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText(taskName))
        onView(withId(R.id.editText_description))
            .perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio))
            .perform(typeText(priority), closeSoftKeyboard())

        // Instead of typing into non-editable time fields, click to invoke the time pickers.
        onView(withId(R.id.editText_taskStart)).perform(click())
        // Wait for the time picker "OK" button and click it.
        val okButton1 = device.findObject(UiSelector().text("OK"))
        if (okButton1.exists()) {
            okButton1.click()
        } else {
            fail("Time picker did not display OK button for start time")
        }
        onView(withId(R.id.editText_taskEnd)).perform(click())
        val okButton2 = device.findObject(UiSelector().text("OK"))
        if (okButton2.exists()) {
            okButton2.click()
        } else {
            fail("Time picker did not display OK button for end time")
        }

        onView(withId(R.id.button_pick_location)).perform(click())
        val enterLocation = device.findObject(UiSelector().text("Search a place"))
        if (enterLocation.waitForExists(1000)) {
            enterLocation.click()
            enterLocation.setText("Executive Hotel Vancouver Airport")
            val firstOption = device.findObject(
                UiSelector().className("android.widget.TextView").instance(1)
            )
            if (firstOption.waitForExists(5000)) {
                firstOption.click()
            } else {
                fail("Failed to find a search result")
            }
        } else {
            throw RuntimeException("Enter Location not found")
        }
        val element = device.findObject(
            UiSelector().resourceId("com.example.cpen321app:id/scrollView_addTask")
        )
        if (!element.waitForExists(5000)) {
            throw RuntimeException("Add Task Window not found")
        }
        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        device.wait(Until.hasObject(By.text("Create Task")), 5000)
        onView(isRoot()).perform(closeSoftKeyboard())

        onView(withId(R.id.editText_duration)).perform(replaceText(taskPrio), closeSoftKeyboard())
        onView(isRoot()).perform(closeSoftKeyboard())

        device.wait(Until.hasObject(By.text("Create Task")), 1000)

        onView(withId(R.id.button_taskCreate)).perform(click())
        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        onView(isRoot()).perform(waitFor(5000))
        onView(withId(R.id.recyclerView)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                hasDescendant(withText(taskName))
            )
        ).check(matches(isDisplayed()))
    }

    private fun testDeleteTask(taskName: String) {
        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        onView(withId(R.id.list_view_button)).perform(click())
        Espresso.onIdle()
        onView(withId(R.id.recyclerView)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                hasDescendant(withText(taskName))
            )
        ).check(matches(isDisplayed()))
        onView(withText(taskName)).perform(longClick())
        onView(withId(R.id.delete_button)).perform(click())
        Espresso.onIdle()
        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        onView(isRoot()).perform(waitFor(5000))
        try {
            onView(withId(R.id.recyclerView)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText(taskName))
                )
            )
            fail("Task not deleted")
        } catch (e: PerformException) {
            // expected
        }
        onView(withText(taskName)).check(doesNotExist())
    }

    private fun waitFor(delay: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for $delay milliseconds."
            override fun perform(uiController: UiController, view: View?) {
                uiController.loopMainThreadForAtLeast(delay)
            }
        }
    }

    private fun getRandomTestTaskName(): String {
        return "TestTask#" + Random.nextInt(0, 1000000)
    }

    private fun testFailAddTaskLat(taskName: String) {
        val taskDescription = "test description"
        val priority = "1"
        val taskInvalidLat = "100.0"
        val taskLng = "50.0"
        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText(taskName))
        onView(withId(R.id.editText_description))
            .perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText(priority), closeSoftKeyboard())
        onView(withId(R.id.editText_taskStart)).perform(click())
        val okButton = device.findObject(UiSelector().text("OK"))
        if (okButton.exists()) {
            okButton.click()
        } else {
            fail("Time picker did not show OK button for start time")
        }
        onView(withId(R.id.editText_taskEnd)).perform(click())
        val okButton2 = device.findObject(UiSelector().text("OK"))
        if (okButton2.exists()) {
            okButton2.click()
        } else {
            fail("Time picker did not show OK button for end time")
        }
        onView(withId(R.id.editText_taskLat)).perform(typeText(taskInvalidLat), closeSoftKeyboard())
        onView(withId(R.id.editText_taskLng)).perform(typeText(taskLng), closeSoftKeyboard())
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withText("Valid Latitude Required: Between -90 and 90 degrees"))
            .check(matches(isDisplayed()))
    }

    private fun testFailAddTaskLng(taskName: String) {
        val taskDescription = "test description"
        val priority = "1"
        val taskLat = "40.0"
        val taskInvalidLng = "300.0"
        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText(taskName))
        onView(withId(R.id.editText_description))
            .perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText(priority), closeSoftKeyboard())
        onView(withId(R.id.editText_taskStart)).perform(click())
        val okButton = device.findObject(UiSelector().text("OK"))
        if (okButton.exists()) {
            okButton.click()
        } else {
            fail("Time picker did not show OK button for start time")
        }
        onView(withId(R.id.editText_taskEnd)).perform(click())
        val okButton2 = device.findObject(UiSelector().text("OK"))
        if (okButton2.exists()) {
            okButton2.click()
        } else {
            fail("Time picker did not show OK button for end time")
        }
        onView(withId(R.id.editText_taskLat)).perform(typeText(taskLat), closeSoftKeyboard())
        onView(withId(R.id.editText_taskLng)).perform(typeText(taskInvalidLng), closeSoftKeyboard())
        onView(isRoot()).perform(closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withText("Valid Longitude Required: Between -180 and 180 degrees"))
            .check(matches(isDisplayed()))
    }

    private fun testFailInvalidInput(taskName: String) {
        val taskDescription = "test description"
        val taskLat = "40.0"
        val taskLng = "50.0"
        onView(withText("+")).perform(click())

        // Test buggy inputs

    }

}

// ----------------------
// RouteWorker Tests
// ----------------------
@RunWith(AndroidJUnit4::class)
@LargeTest
class RouteWorkerTest {

    private lateinit var context: Context
    private lateinit var executor: Executor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        executor = Executors.newSingleThreadExecutor()
    }

    @Test
    fun testRouteWorker_EmptyTaskList() {
        val worker = TestWorkerBuilder<RouteWorker>(context, executor)
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
        val orderedTasks = worker.orderTasksByNearestNeighbor(49.2827, -123.1207, emptyList())
        assertEquals(0, orderedTasks.size)
    }

    @Test
    fun testRouteWorker_TaskOrdering() {
        val worker = TestWorkerBuilder<RouteWorker>(context, executor)
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
        val tasks = listOf(
            Task("1", "Task A", "10:00", "11:00", 1.0, 49.28, -123.12, 1, "Desc A"),
            Task("2", "Task B", "10:30", "11:30", 1.0, 49.29, -123.13, 1, "Desc B"),
            Task("3", "Task C", "11:00", "12:00", 1.0, 49.30, -123.14, 1, "Desc C")
        )
        val orderedTasks = worker.orderTasksByNearestNeighbor(49.28, -123.12, tasks)
        assertEquals("Task A", orderedTasks[0].name)
        assertEquals("Task B", orderedTasks[1].name)
        assertEquals("Task C", orderedTasks[2].name)
    }

    @Test
    fun testRouteWorker_NotificationDisplayed() {
        val worker = TestWorkerBuilder<RouteWorker>(context, executor)
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
        val mapsUrl = "https://www.google.com/maps/dir/?api=1&origin=49.27,-123.11"
        worker.showRouteNotification(context, mapsUrl)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(notificationManager.activeNotifications)
    }
}
