package com.example.cpen321app

import com.example.cpen321app.TestUtils.hasDataUriContaining
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class RouteManagementTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // For simplicity, we assume that TestUtils.clickOnViewWithId() is implemented in your TestUtils object.
    // Also, ensure that TestUtils.navigateToTaskList() and other helper methods are defined.

    @Before
    fun setUp() {
        Intents.init()
        setupMocks()
        injectMockDependencies()
        navigateToTaskList()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun routePlanningButton_isDisplayed() {
        onView(withId(R.id.buttonPlanRoute)).check(matches(isDisplayed()))
    }

    @Test
    fun planRoute_withSelectedTasks_showsRouteDialog() {
        // Select first task from the RecyclerView
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
                    0, TestUtils.clickOnViewWithId(R.id.checkBox_select)
                )
            )
        // Click on Plan Route button
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        // Verify that the route confirmation dialog is displayed.
        onView(withText(containsString("Route"))).check(matches(isDisplayed()))
    }

    @Test
    fun acceptRoute_launchesGoogleMaps() {
        val mockRouteUrl = "https://www.google.com/maps/dir/?api=1&origin=49.2606,-123.2460&destination=49.2827,-123.1207"
        TestUtils.setupMockRouteResponse(mockRouteUrl)
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
                    0, TestUtils.clickOnViewWithId(R.id.checkBox_select)
                )
            )
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        onView(withText("Accept")).perform(click())
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasDataUriContaining("maps/dir")
            )
        )
    }

    @Test
    fun routeOptimization_ordersTasksByDistance() {
        // Select two tasks in the list.
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
                    0, TestUtils.clickOnViewWithId(R.id.checkBox_select)
                )
            )
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
                    1, TestUtils.clickOnViewWithId(R.id.checkBox_select)
                )
            )
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        onView(withText(containsString("Route")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun viewRoute_displaysRouteOnMap() {
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<ViewHolder>(
                    0, TestUtils.clickOnViewWithId(R.id.checkBox_select)
                )
            )
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        onView(withText("View on Map")).perform(click())
        onView(withId(R.id.map)).check(matches(isDisplayed()))
    }

    @Test
    fun planRoute_withNoTasks_showsError() {
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        onView(withText(containsString("select tasks")))
            .check(matches(isDisplayed()))
    }

    // Stub helper methods.
    private fun setupMocks() {
        // TODO: Create and configure any required mocks.
    }

    private fun injectMockDependencies() {
        // TODO: If you need to inject mocks into your MainActivity or workers, do it here.
    }

    private fun navigateToTaskList() {
        // Use TestUtils' navigation method.
        TestUtils.navigateToTaskList()
    }
}
