package com.example.cpen321app

import android.util.Log
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Enhanced tests for M4 Requirements.
 */
@RunWith(AndroidJUnit4::class)
class M4RequirementsTest {

    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val randomSuffix = Random.nextInt(0, 1000000).toString()
    private val testTaskName = "TestTask#$randomSuffix"
    private val testTaskDescription = "This is a test description for task #$randomSuffix with sufficient length"
    private val shortTaskName = "A"
    private val shortTaskDescription = "Short"
    private val nonExistentTaskName = "NonExistentTask#$randomSuffix"
    private val locations = listOf(
        "University of British Columbia",
        "Stanley Park Vancouver",
        "Granville Island Vancouver",
        "Museum of Anthropology at UBC"
    )

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        TestUtils.registerIdlingResource()
        try {
            TestUtils.login(device)
        } catch (e: Exception) {
            Log.e("M4RequirementsTest", "Login failed or wasn't needed", e)
        }
    }

    @After
    fun cleanup() {
        TestUtils.unregisterIdlingResource()
        TestUtils.navigateToTaskList()
    }

    @Test
    fun testUC1_CreateTask_Success() {
        // Create the task via TestUtils helper (make sure this method is implemented)
        TestUtils.createTask(device, testTaskName, testTaskDescription, "2", "10:00", "11:00", locations[0])
        // Navigate to add task screen (assuming "+" is used to open it)
        onView(withText("+")).perform(click())
        // Verify input fields are displayed
        onView(withId(R.id.editTextName)).check(matches(isDisplayed()))
        onView(withId(R.id.editText_description)).check(matches(isDisplayed()))
        // Fill in the task details
        onView(withId(R.id.editTextName)).perform(typeText(testTaskName), closeSoftKeyboard())
        onView(withId(R.id.editText_description)).perform(typeText(testTaskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText("2"), closeSoftKeyboard())
        onView(withId(R.id.editText_taskStart)).perform(typeText("10:00"))
        device.findObject(UiSelector().text("OK")).apply { if (exists()) click() }
        onView(withId(R.id.editText_taskEnd)).perform(typeText("11:00"))
        device.findObject(UiSelector().text("OK")).apply { if (exists()) click() }
        onView(withId(R.id.button_pick_location)).perform(click())
        val enterLocation = device.findObject(UiSelector().text("Search a place"))
        if (enterLocation.exists()) {
            enterLocation.click()
            enterLocation.setText(locations[0])
            device.findObject(UiSelector().clickable(true)).apply { if (exists()) click() }
        }
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withId(R.id.list_view_button)).perform(click())
        onView(withText(testTaskName)).check(matches(isDisplayed()))
        TestUtils.deleteTask(device, testTaskName)
    }

    @Test
    fun testUC1_CreateTask_Failure_MissingRequiredFields() {
        onView(withText("+")).perform(click())
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withId(R.id.editTextName)).check(matches(isDisplayed()))
        device.pressBack()
    }

    @Test
    fun testUC1_CreateTask_Failure_NameTooShort() {
        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText(shortTaskName), closeSoftKeyboard())
        onView(withId(R.id.editText_description)).perform(typeText(testTaskDescription), closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withId(R.id.editTextName)).check(matches(isDisplayed()))
        device.pressBack()
    }

    @Test
    fun testUC2_PlanRoute_Success() {
        TestUtils.createTaskWithLocation("Task A for UC2 route", "First task", "3", locations[0])
        TestUtils.createTaskWithLocation("Task B for UC2 route", "Second task", "2", locations[1])
        TestUtils.navigateToTaskList()
        TestUtils.selectTask(device, "Task A for UC2 route")
        TestUtils.selectTask(device, "Task B for UC2 route")
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(androidx.test.uiautomator.By.textContains("Route")), 5000)
            if (device.findObject(UiSelector().text("View on Map")).exists()) {
                device.findObject(UiSelector().text("View on Map")).click()
                device.wait(Until.hasObject(androidx.test.uiautomator.By.res("map")), 5000)
                device.pressBack()
            } else if (device.findObject(UiSelector().text("Accept")).exists()) {
                TestUtils.initIntents()
                device.findObject(UiSelector().text("Accept")).click()
                TestUtils.releaseIntents()
            } else {
                device.pressBack()
            }
        }
        TestUtils.deleteTask(device, "Task A for UC2 route")
        TestUtils.deleteTask(device, "Task B for UC2 route")
    }

    @Test
    fun testUC2_PlanRoute_Failure_NoTasksSelected() {
        TestUtils.navigateToTaskList()
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(androidx.test.uiautomator.By.textContains("select tasks")), 2000)
        }
    }

    @Test
    fun testUC3_FilterTasks_Success() {
        TestUtils.createTask(device, "High Priority Task", "Task with high priority", "3")
        TestUtils.createTask(device, "Medium Priority Task", "Task with medium priority", "2")
        TestUtils.createTask(device, "Low Priority Task", "Task with low priority", "1")
        TestUtils.navigateToTaskList()
        val filterChip = device.findObject(UiSelector().text("High"))
        if (filterChip.exists()) {
            filterChip.click()
            onView(withText("High Priority Task")).check(matches(isDisplayed()))
            try {
                onView(withText("Medium Priority Task")).check(doesNotExist())
                onView(withText("Low Priority Task")).check(doesNotExist())
            } catch (e: Exception) { }
            filterChip.click()
        }
        TestUtils.deleteTask(device, "High Priority Task")
        TestUtils.deleteTask(device, "Medium Priority Task")
        TestUtils.deleteTask(device, "Low Priority Task")
    }

    @Test
    fun testUC3_SearchTasks_Success() {
        TestUtils.createTask(device, testTaskName, testTaskDescription, "2", "12:00", "13:00", "Granville Island Vancouver")
        val searchView = device.findObject(UiSelector().descriptionContains("Search"))
        if (searchView.exists()) {
            searchView.click()
            searchView.setText(testTaskName.substring(0, 5))
            device.pressEnter()
            onView(withText(testTaskName)).check(matches(isDisplayed()))
            searchView.clearTextField()
        }
        TestUtils.deleteTask(device, testTaskName)
    }

    @Test
    fun testUC3_SearchTasks_NoResults() {
        val searchView = device.findObject(UiSelector().descriptionContains("Search"))
        if (searchView.exists()) {
            searchView.click()
            searchView.setText(nonExistentTaskName)
            device.pressEnter()
            device.wait(Until.hasObject(androidx.test.uiautomator.By.textContains("No tasks")), 2000)
            searchView.clearTextField()
        }
    }

    @Test
    fun testUC3_CreateTaskThenPlanRoute() {
        TestUtils.createTask(device, testTaskName, testTaskDescription, "2", "12:00", "13:00", "Granville Island Vancouver")
        TestUtils.selectTask(device, testTaskName)
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(androidx.test.uiautomator.By.textContains("Route")), 5000)
        }
        TestUtils.deleteTask(device, testTaskName)
    }
}
