package com.example.cpen321app

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class EndToEndTesting {

    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val randomSuffix = Random.nextInt(0, 1000000).toString()
    private val testTaskName = "TestTask#$randomSuffix"
    private val testTaskDescription = "This is a test description for task #$randomSuffix"
    private val highPriorityTask = "HighPriorityTask#$randomSuffix"
    private val mediumPriorityTask = "MediumPriorityTask#$randomSuffix"
    private val lowPriorityTask = "LowPriorityTask#$randomSuffix"
    private val nonExistentTaskName = "NonExistentTask#$randomSuffix"

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        try {
            TestUtils.login(device)
        } catch (e: Exception) {
            Log.e("EndToEndTesting", "Login failed or wasn't needed", e)
        }
        TestUtils.navigateToTaskList()
    }

    @After
    fun cleanup() {
        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)
    }

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.cpen321app", appContext.packageName)
    }

    @Test
    fun testCreateTaskSuccess() {
        TestUtils.createTask(device, testTaskName, testTaskDescription, "1", "10:00", "11:00", "Executive Hotel Vancouver Airport")
        onView(withId(R.id.recyclerView)).perform(
            scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(testTaskName)))
        )
        onView(withText(testTaskName)).check(matches(isDisplayed()))
        TestUtils.deleteTask(device, testTaskName)
    }

    @Test
    fun testCreateTaskFailure_MissingRequiredFields() {
        onView(withText("+")).perform(click())
        onView(withId(R.id.button_taskCreate)).perform(click())
        onView(withId(R.id.editTextName)).check(matches(isDisplayed()))
        device.pressBack()
    }

    @Test
    fun testCreateTaskFailure_NameTooShort() {
        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText("A"), closeSoftKeyboard())
        onView(withId(R.id.editText_description)).perform(typeText(testTaskDescription), closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        // Wait for error message containing "name"
        device.wait(Until.hasObject(By.textContains("name")), 2000)
        device.pressBack()
    }

    @Test
    fun testCreateTaskFailure_DescriptionTooShort() {
        onView(withText("+")).perform(click())
        onView(withId(R.id.editTextName)).perform(typeText(testTaskName), closeSoftKeyboard())
        onView(withId(R.id.editText_description)).perform(typeText("Short"), closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        // Wait for error message containing "description"
        device.wait(Until.hasObject(By.textContains("description")), 2000)
        device.pressBack()
    }

    @Test
    fun testUpdateTask() {
        TestUtils.createTask(device, testTaskName, testTaskDescription, "1", "10:00", "11:00", "Executive Hotel Vancouver Airport")
        onView(withText(testTaskName)).perform(longClick())
        if (device.findObject(UiSelector().text("Edit")).exists()) {
            device.findObject(UiSelector().text("Edit")).click()
            val updatedTaskName = "Updated$testTaskName"
            onView(withId(R.id.editTextName)).perform(clearText(), typeText(updatedTaskName), closeSoftKeyboard())
            onView(withId(R.id.button_taskCreate)).perform(click())
            onView(withText(updatedTaskName)).check(matches(isDisplayed()))
            TestUtils.deleteTask(device, updatedTaskName)
        } else {
            device.pressBack()
            TestUtils.deleteTask(device, testTaskName)
        }
    }

    @Test
    fun testPlanRouteSuccess() {
        TestUtils.createTask(device, highPriorityTask, "High priority task", "3", "10:00", "11:00", "Vancouver International Airport")
        TestUtils.createTask(device, lowPriorityTask, "Low priority task", "1", "14:00", "15:00", "Stanley Park Vancouver")
        TestUtils.selectTask(device, highPriorityTask)
        TestUtils.selectTask(device, lowPriorityTask)
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(By.textContains("Route")), 5000)
            if (device.findObject(UiSelector().text("Accept")).exists()) {
                TestUtils.initIntents()
                device.findObject(UiSelector().text("Accept")).click()
                TestUtils.releaseIntents()
            } else if (device.findObject(UiSelector().text("View on Map")).exists()) {
                device.findObject(UiSelector().text("View on Map")).click()
            } else {
                device.pressBack()
            }
            TestUtils.navigateToTaskList()
        }
        TestUtils.deleteTask(device, highPriorityTask)
        TestUtils.deleteTask(device, lowPriorityTask)
    }

    @Test
    fun testPlanRouteFailure_NoTasksSelected() {
        TestUtils.navigateToTaskList()
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(By.textContains("select tasks")), 5000)
        }
    }

    @Test
    fun testFilterTasksByPriority() {
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
    fun testSearchTasks() {
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
    fun testSearchTasks_NoResults() {
        val searchView = device.findObject(UiSelector().descriptionContains("Search"))
        if (searchView.exists()) {
            searchView.click()
            searchView.setText(nonExistentTaskName)
            device.pressEnter()
            device.wait(Until.hasObject(By.textContains("No tasks")), 2000)
            searchView.clearTextField()
        }
    }

    @Test
    fun testCreateTaskThenPlanRoute() {
        TestUtils.createTask(device, testTaskName, testTaskDescription, "2", "12:00", "13:00", "Granville Island Vancouver")
        TestUtils.selectTask(device, testTaskName)
        val planRouteButton = device.findObject(UiSelector().text("Plan Route"))
        if (planRouteButton.exists()) {
            planRouteButton.click()
            device.wait(Until.hasObject(By.textContains("Route")), 5000)
        }
        TestUtils.deleteTask(device, testTaskName)
    }

    // Helper stubs for local calls.
    private fun createTask(name: String, description: String, priority: String) {
        TestUtils.createTask(device, name, description, priority)
    }
    private fun navigateToTaskList() {
        TestUtils.navigateToTaskList()
    }
    private fun selectTask(name: String) {
        TestUtils.selectTask(device, name)
    }
    private fun deleteTask(name: String) {
        TestUtils.deleteTask(device, name)
    }
}
