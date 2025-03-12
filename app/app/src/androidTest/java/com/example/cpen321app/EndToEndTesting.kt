package com.example.cpen321app

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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
import org.junit.After

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

        val taskName = "TestTask#" + Random.nextInt(0,1000000).toString()
        // Add Task
        testAddTask(taskName)

        testDeleteTask(taskName)

    }

    private fun login() {

//        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.wait(
            Until.hasObject(By.text("Choose an account")), 10000
        )
        val button = device.findObject(UiSelector().clickable(true))
        if(button.exists()) {
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
            fail("No precise location option")
        }
        val whileUsingTheApp = device.findObject(UiSelector().text("While using the app"))
        if(whileUsingTheApp.exists()) {
            whileUsingTheApp.click()
        } else {
            fail("No 'While using the app' option")
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
        if(enterLocation.exists()) {
            enterLocation.click()
            enterLocation.setText("Executive Hotel Vancouver Airport")
            val firstOption = device.findObject(UiSelector().clickable(true))
            if(firstOption.exists()) {
                firstOption.click()
            } else {
                fail("Failed to find a search result")
            }
        }
        val element = device.findObject(UiSelector().resourceId("com.example.cpen321app:id/scrollView_addTask"))
        if(!element.waitForExists(5000)) {
            throw RuntimeException("Add Task Window not found")
        }

        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)

        onView(withId(R.id.button_taskCreate)).perform(click())

        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)

//        // This is janky but works for now. These buttons shouldn't need to be pressed and should have an async wait.
//        onView(withId(R.id.list_view_button)).perform(click())
//        onView(withId(R.id.list_view_button)).perform(click())

//        // copilot generated
//        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
//            hasDescendant(withText(taskName))
//            )).check(matches(isDisplayed()))
    }

    private fun testDeleteTask(taskName: String) {

        IdlingRegistry.getInstance().register(TaskViewModel.IdlingResourceManager.countingIdlingResource)
        onView(withId(R.id.list_view_button)).perform(click())

        Espresso.onIdle()

        onView(withText(taskName)).perform(longClick())
        onView(withId(R.id.delete_button)).perform(click())
        IdlingRegistry.getInstance().unregister(TaskViewModel.IdlingResourceManager.countingIdlingResource)

        Espresso.onIdle()

        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
            hasDescendant(withText(taskName))
        )).check(doesNotExist())

    }

    private fun testUpdateTask() {

    }

}





//@RunWith(AndroidJUnit4::class)
//@LargeTest
//class HelloWorldEspressoTest {
//
//    @get:Rule
//    val activityRule = ActivityScenarioRule(MainActivity::class.java)
//
//    @Test fun listGoesOverTheFold() {
//        onView(withText("Hello world!")).check(matches(isDisplayed()))
//    }
//}