package com.example.cpen321app

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.intent.Intents
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import okhttp3.OkHttpClient
import okhttp3.Response
import androidx.work.Worker
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.mockito.Mockito.mock

object TestUtils {
    // Register/unregister idling resources if needed.
    fun registerIdlingResource() { /* Implement if using an IdlingResource */ }
    fun unregisterIdlingResource() { /* Implement if using an IdlingResource */ }

    // Initialize and release Espresso Intents.
    fun initIntents() {
        Intents.init()
    }
    fun releaseIntents() {
        Intents.release()
    }

    // Simulate a login flow for tests.
    fun login(device: UiDevice) { /* Stub: implement if needed */ }

    // Navigate to the task list screen.
    fun navigateToTaskList() { /* Stub: implement navigation steps as needed */ }

    /**
     * Simulate creating a task via UI interactions.
     * Overloaded version accepting all parameters.
     */
    fun createTask(
        device: UiDevice,
        name: String,
        description: String,
        priority: String,
        startTime: String,
        endTime: String,
        location: String
    ) {
        // Stub: You may simulate navigating to AddTask screen and entering the fields.
    }

    /**
     * Overloaded version with fewer parameters.
     */
    fun createTask(
        device: UiDevice,
        name: String,
        description: String,
        priority: String
    ) {
        createTask(device, name, description, priority, "10:00", "11:00", "Default Location")
    }

    // Simulate deleting a task.
    fun deleteTask(device: UiDevice, name: String) { /* Stub: implement deletion simulation */ }

    // Simulate creating a task with a location.
    fun createTaskWithLocation(name: String, description: String, priority: String, location: String) { /* Stub */ }

    // Simulate selecting a task.
    fun selectTask(device: UiDevice, name: String) {
        // Stub: implement selection simulation (e.g. by finding the task name and clicking its checkbox)
    }

    // Simulate confirming a time picker dialog by clicking the OK button.
    fun simulateTimePickerConfirmation(time: String) {
        // In this stub we simply click the "OK" button.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val okButton = device.findObject(UiSelector().text("OK"))
        if (okButton.exists()) {
            okButton.click()
        }
    }

    // Custom ViewAction to click a child view inside a RecyclerView item.
    fun clickOnViewWithId(viewId: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints() = androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(View::class.java)
            override fun getDescription() = "Click on a child view with specified id."
            override fun perform(uiController: UiController, view: View) {
                val child: View = view.findViewById(viewId)
                child.performClick()
            }
        }
    }

    // Custom matcher: check that a SwipeRefreshLayout is not refreshing.
    fun notRefreshing() = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("SwipeRefreshLayout is not refreshing")
        }
        override fun matchesSafely(item: View): Boolean {
            return item is SwipeRefreshLayout && !item.isRefreshing
        }
    }

    // Custom matcher: check RecyclerView has at least 'count' children.
    fun hasMinimumChildCount(count: Int) = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("RecyclerView should have at least $count children")
        }
        override fun matchesSafely(view: View): Boolean {
            return view is RecyclerView && view.childCount >= count
        }
    }

    // Create a mock TaskViewModel for testing.
    fun createMockViewModel(): TaskViewModel {
        return mock(TaskViewModel::class.java)
    }

    // Inject mocks into a Worker via reflection (if needed)
    fun injectWorkerMocks(worker: Worker, fusedLocationClient: Any?, okHttpClient: OkHttpClient) {
        try {
            val field = worker.javaClass.getDeclaredField("client")
            field.isAccessible = true
            field.set(worker, okHttpClient)
        } catch (e: Exception) {
            // Field might not exist or be named differently.
        }
    }

    /**
     * Custom matcher to verify that an Intent's data URI contains the expected substring.
     */
    fun hasDataUriContaining(expectedSubstring: String): Matcher<Intent> {
        return object : TypeSafeMatcher<Intent>() {
            override fun describeTo(description: Description) {
                description.appendText("has data URI containing \"$expectedSubstring\"")
            }
            override fun matchesSafely(intent: Intent): Boolean {
                val data: Uri? = intent.data
                return data?.toString()?.contains(expectedSubstring) == true
            }
        }
    }

    // Stub functions for worker tests:
    fun setupLocationMockResponse(worker: Worker) { /* Stub */ }
    fun setupHttpMockResponse(worker: Worker, response: Response) { /* Stub */ }
    fun setupNoLocationPermission(worker: Worker) { /* Stub */ }
    fun setupLocationUnavailable(worker: Worker) { /* Stub */ }
    fun setupHttpRequestFailure(worker: Worker) { /* Stub */ }
    fun setupMockRouteResponse(routeUrl: String) { /* Stub */ }
}
