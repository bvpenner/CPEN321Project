package com.example.cpen321app

import android.location.Location
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class TaskListFragmentTest : TaskAdapter.OnItemLongClickListener {

    private lateinit var mockViewModel: TaskViewModel
    private lateinit var mockLocation: Location

    @Before
    fun setUp() {
        // Initialize Espresso Intents (if your tests require it)
        Intents.init()
        // Optionally simulate a login if your app requires authentication.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        TestUtils.login(device)
        // Create a mock TaskViewModel using TestUtils.
        mockViewModel = TestUtils.createMockViewModel()
        // Launch the TaskListFragment and inject the mock ViewModel using reflection.
        val scenario = launchFragmentInContainer<TaskListFragment>()
        scenario.onFragment { fragment ->
            val field = TaskListFragment::class.java.getDeclaredField("taskViewModel")
            field.isAccessible = true
            field.set(fragment, mockViewModel)
        }
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun taskList_isDisplayedCorrectly() {
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        onView(withId(R.id.recyclerView)).check(matches(TestUtils.hasMinimumChildCount(1)))
    }

    @Test
    fun planRoute_withSelectedTasks_showsConfirmationDialog() {
        // Select two tasks by clicking their checkboxes.
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                0, TestUtils.clickOnViewWithId(R.id.checkBox_select)
            ))
        onView(withId(R.id.recyclerView))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                1, TestUtils.clickOnViewWithId(R.id.checkBox_select)
            ))
        // Tap the "Plan Route" button.
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        // Verify that a dialog containing the text "Route" is displayed.
        onView(withText(containsString("Route"))).check(matches(isDisplayed()))
    }

    @Test
    fun planRoute_withNoTasksSelected_showsErrorMessage() {
        // Simply tap the "Plan Route" button when no task is selected.
        onView(withId(R.id.buttonPlanRoute)).perform(click())
        // Verify that an error message containing "Please select tasks" is displayed.
        onView(withText(containsString("Please select tasks"))).check(matches(isDisplayed()))
    }

    // Remove any tests that reference views not present in TaskListFragment (like addTaskFab).
    // Override the onItemLongClick from TaskAdapter.OnItemLongClickListener.
    override fun onItemLongClick(task: Task): Boolean = true
}
