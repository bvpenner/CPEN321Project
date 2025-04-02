package com.example.cpen321app

import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class RoundTripTimeTesting : BaseUITest() {

    companion object {
        val TAG = "RoundTripTimeTesting"
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testRTT() {
        login()
        // Handle the location permission prompt, if it appears
        handleLocationSettingsPrompt()
        navigateToTaskList()
        val taskName = getRandomTestTaskName()
        val taskNameTwo = getRandomTestTaskName()

        addTask(taskName, "test description", "1", "60")
        addTask(taskNameTwo, "test description", "1", "60")

        navigateToTaskList()
        navigateToTaskList()

        verifyTaskExists(taskName)
        verifyTaskExists(taskNameTwo)
        createdTestTasks.add(taskName) // Track for cleanup
        createdTestTasks.add(taskNameTwo) // Track for cleanup
//        createdTestTasks.add(taskNameTwo)

        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
//        do {
//
//        } while(!isTextInRecyclerView(taskName))

        onView(withId(R.id.recyclerView)).perform(scrollTo<ViewHolder>(hasDescendant(withText(taskName))))
        onView(isRoot()).perform(waitFor(5000))
        clickCheckBoxWithText(taskName)
        onView(isRoot()).perform(waitFor(5000))

        Log.d(TAG, "Passes checking first box")

        onView(withId(R.id.recyclerView)).perform(scrollTo<ViewHolder>(hasDescendant(withText(taskNameTwo))))
        onView(isRoot()).perform(waitFor(5000))
        clickCheckBoxWithText(taskNameTwo)
        onView(isRoot()).perform(waitFor(5000))

        Log.d(TAG, "Passes checking second box")

        onView(isRoot()).perform(waitFor(10000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
//        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem<ViewHolder>(hasDescendant(withText(taskName)), clickCheckBoxInsideLinearLayout(R.id.taskLayout, R.id.checkBox_select)))

        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

//        // ChatGPT generated
//        onView(withId(R.id.recyclerView))
//            .perform(scrollTo<ViewHolder>(hasDescendant(withText(taskNameTwo))))
//        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load



//        // ChatGPT generated
//        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem<ViewHolder>(hasDescendant(withText(taskNameTwo)), clickCheckBoxInsideLinearLayout(R.id.taskLayout, R.id.checkBox_select)))

        onView(isRoot()).perform(waitFor(2000))

        onView(withId(R.id.buttonPlanRoute)).perform(click())

        onView(isRoot()).perform(waitFor(5000))

        deleteTask(taskName)
        deleteTask(taskNameTwo)
        verifyTaskDeleted(taskName)
        createdTestTasks.remove(taskName) // No longer needs cleanup
        createdTestTasks.remove(taskNameTwo)
    }

    private fun handleLocationSettingsPrompt() {
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

    private fun clickCheckBoxInsideLinearLayout(layoutId: Int, checkBoxId: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isAssignableFrom(View::class.java)
            }

            override fun getDescription(): String {
                return "Click checkbox inside LinearLayout in RecyclerView"
            }

            override fun perform(uiController: UiController?, view: View?) {
                // Find the LinearLayout inside the RecyclerView item
                val linearLayout = view?.findViewById<LinearLayout>(layoutId)
                linearLayout?.let {
                    val checkBox = it.findViewById<CheckBox>(checkBoxId)
                    checkBox?.performClick()
                }
            }
        }
    }

    // ChatGPT generated
    private fun isTextInRecyclerView(text: String): Boolean {
        return try {
            onView(withId(R.id.recyclerView))
                .check(matches(hasDescendant(withText(text))))
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    // ChatGPT generated
    private fun clickCheckBoxWithText(targetText: String) {
        onView(allOf(
            withId(R.id.checkBox_select),  // Find a CheckBox
            hasSibling(hasDescendant(withText(targetText))) // Ensure it's next to the text
        )).perform(click())
    }


}