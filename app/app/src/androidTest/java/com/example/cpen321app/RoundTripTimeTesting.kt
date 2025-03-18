package com.example.cpen321app

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
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
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

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testRTT() {
        login()
        // Handle the location permission prompt, if it appears
        handleLocationSettingsPrompt()
        navigateToTaskList()
        val taskName = getRandomTestTaskName()
        val taskName2 = getRandomTestTaskName()

        addTask(taskName, "test description", "1", "60")
        addTask(taskName2, "test description", "1", "60")
        verifyTaskExists(taskName)
        createdTestTasks.add(taskName) // Track for cleanup
//        createdTestTasks.add(taskName2)

        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
        onView(withId(R.id.recyclerView))
            .perform(scrollTo<ViewHolder>(hasDescendant(withText(taskName))))
        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItem<ViewHolder>(
                    hasDescendant(withText(taskName)),
                    clickCheckBoxInsideLinearLayout(R.id.taskLayout, R.id.checkBox_select)
                )
            )

        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
        onView(withId(R.id.recyclerView))
            .perform(scrollTo<ViewHolder>(hasDescendant(withText(taskName2))))
        onView(isRoot()).perform(waitFor(5000)) // Wait 500ms for RecyclerView to load

        // ChatGPT generated
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItem<ViewHolder>(
                    hasDescendant(withText(taskName2)),
                    clickCheckBoxInsideLinearLayout(R.id.taskLayout, R.id.checkBox_select)
                )
            )

        onView(isRoot()).perform(waitFor(2000))

        onView(withId(R.id.buttonPlanRoute)).perform(click())

        onView(isRoot()).perform(waitFor(5000))

        deleteTask(taskName)
        deleteTask(taskName2)
        verifyTaskDeleted(taskName)
        createdTestTasks.remove(taskName) // No longer needs cleanup
        createdTestTasks.remove(taskName2)
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


}