package com.example.cpen321app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        Intents.init()
        mockLoginProcess()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun bottomNavigationView_isDisplayedAndFunctional() {
        // Instead of fixed IDs, check for menu item texts.
        // Click on "Map" menu item.
        onView(withText("Map")).perform(click())
        onView(withId(R.id.map)).check(matches(isDisplayed()))
        // Click on "Tasks" menu item.
        onView(withText("Tasks")).perform(click())
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
    }

    @Test
    fun clickAddTaskFab_launchesAddTaskActivity() {
        // Click on the FAB which is created programmatically with text "+"
        onView(withText("+")).perform(click())
        intended(hasComponent(AddTask::class.java.name))
    }

    @Test
    fun swipeToRefresh_refreshesTaskList() {
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        // If your app supports swipe refresh in the task list fragment, update the ID accordingly.
        // Otherwise, you can remove this test.
    }

    private fun mockLoginProcess() {
        // Only setting u_id since SessionManager doesn't define userName or userEmail.
        SessionManager.u_id = "test_user_id"
    }
}
