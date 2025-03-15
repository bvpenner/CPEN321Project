package com.example.cpen321app

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.toPackage
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import okhttp3.internal.wait
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito
import kotlin.random.Random

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 *
 * copilot helped write the UIAutomator code
 */
@RunWith(AndroidJUnit4::class)
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
    fun testCreateTask_InvalidInput() {

    }


    private fun login() {

//        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.wait(
            Until.hasObject(By.text("Choose an account")), 10000
        )
        val button = device.findObject(UiSelector().clickable(true))
        if(button.waitForExists(4000)) {
            button.click()
        } else {
            fail("No provided account to log in with")
        }

        device.wait(
            Until.hasObject(By.text("Allow cpen321app to access this device's location?")), 10000
        )
        val precise = device.findObject(UiSelector().text("Precise"))
        if(precise.exists()) {
            precise.click()
        } else {
//            fail("No precise location option")
        }
        val whileUsingTheApp = device.findObject(UiSelector().text("While using the app"))
        if(whileUsingTheApp.exists()) {
            whileUsingTheApp.click()
        } else {
//            fail("No 'While using the app' option")
        }
    }

    private fun testAddTask(taskName: String) {

        val taskDescription = "test description"
        val priority = "1"

        onView(withText("+")).perform(click())

        onView(withId(R.id.editTextName)).perform(typeText(taskName))
        onView(withId(R.id.editText_description)).perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText(priority), closeSoftKeyboard())

        onView(withId(R.id.editText_taskStart)).perform(typeText("11:10"))
        var okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
        }

        onView(withId(R.id.editText_taskEnd)).perform(typeText("12:00"))
        okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
        }

        onView(withId(R.id.button_pick_location)).perform(click())

        val enterLocation = device.findObject(UiSelector().text("Search a place"))
        if(enterLocation.waitForExists(1000)) {
            enterLocation.click()
            enterLocation.setText("Executive Hotel Vancouver Airport")

            val firstOption = device.findObject(UiSelector().className("android.widget.TextView").instance(1))

            if (firstOption.waitForExists(5000)) {
                firstOption.click()
            } else {
                fail("Failed to find a search result")
            }
        } else {
            throw RuntimeException("Enter Location not found")
        }

        val element = device.findObject(UiSelector().resourceId("com.example.cpen321app:id/scrollView_addTask"))
        if(!element.waitForExists(5000)) {
            throw RuntimeException("Add Task Window not found")
        }

        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)

        device.wait(
            Until.hasObject(By.text("Create Task")), 5000
        )

        onView(isRoot()).perform(closeSoftKeyboard())

        onView(withId(R.id.button_taskCreate)).perform(click())

        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)

        onView(isRoot()).perform(waitFor(2000))

        // copilot generated
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(withText(taskName))
            )).check(matches(isDisplayed()))
    }

    private fun testDeleteTask(taskName: String) {

        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        onView(withId(R.id.list_view_button)).perform(click())

        Espresso.onIdle()

        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(withText(taskName))
        )).check(matches(isDisplayed()))

        onView(withText(taskName)).perform(longClick())
        onView(withId(R.id.delete_button)).perform(click())


        Espresso.onIdle()

        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)

        // ChatGPT generated
        onView(isRoot()).perform(waitFor(2000))

        // ChatGPT generated
        try {
            onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                hasDescendant(withText(taskName))
            ))
            fail("Task not deleted")
        } catch (e: PerformException) {
            // do nothing
        }

        onView(withText(taskName)).check(doesNotExist())
    }

    // Written by ChatGPT
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
        val taskName = "TestTask#" + Random.nextInt(0, 1000000).toString()
        return taskName
    }

    private fun testFailAddTaskLat(taskName: String) {

        val taskDescription = "test description"
        val priority = "1"
        val taskInvalidLat = "100.0"
        val taskLng = "50.0"

        onView(withText("+")).perform(click())

        onView(withId(R.id.editTextName)).perform(typeText(taskName))
        onView(withId(R.id.editText_description)).perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText(priority), closeSoftKeyboard())

        onView(withId(R.id.editText_taskStart)).perform(typeText("11:10"))
        var okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
        }

        onView(withId(R.id.editText_taskEnd)).perform(typeText("12:00"))
        okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
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
        onView(withId(R.id.editText_description)).perform(typeText(taskDescription), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio)).perform(typeText(priority), closeSoftKeyboard())

        onView(withId(R.id.editText_taskStart)).perform(typeText("11:10"))
        var okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
        }

        onView(withId(R.id.editText_taskEnd)).perform(typeText("12:00"))
        okbutton = device.findObject(UiSelector().text("OK"))
        if(okbutton.exists()) {
            okbutton.click()
        } else {
            fail("Time input field did not find the OK button on the UI element")
        }

        onView(withId(R.id.editText_taskLat)).perform(typeText(taskLat), closeSoftKeyboard())
        onView(withId(R.id.editText_taskLng)).perform(typeText(taskInvalidLng), closeSoftKeyboard())

        onView(isRoot()).perform(closeSoftKeyboard())

        onView(withId(R.id.button_taskCreate)).perform(click())

        onView(withText("Valid Longitude Required: Between -180 and 180 degrees"))
            .check(matches(isDisplayed()))

    }



}