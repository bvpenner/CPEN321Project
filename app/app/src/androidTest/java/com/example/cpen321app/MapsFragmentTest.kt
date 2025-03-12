package com.example.cpen321app

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.LatLng
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.lifecycle.MutableLiveData
import com.example.cpen321app.TaskViewModel.Companion._taskList

@RunWith(AndroidJUnit4::class)
class MapsFragmentTest {

    @Before
    fun setUp() {
        // Retrieve the Application instance and set up a dummy task list
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GeoTask
        // Create a test task that will result in a marker on the map.
        val tasks = mutableListOf(
            Task(
                id = "1",
                name = "Test Task 1",
                start = "10:00",
                end = "11:00",
                duration = 60.0,
                location_lat = 49.2827,
                location_lng = -123.1207,
                priority = 1,
                description = "Test description"
            )
        )
        // Update the TaskViewModel's LiveData.
        appContext.taskViewModel.apply {
            // Assuming _taskList is accessible (if not, you might provide a public setter for test purposes)
            _taskList.postValue(tasks)
        }
        // Launch the MapsFragment.
        launchFragmentInContainer<MapsFragment>()
    }

    @Test
    fun map_isDisplayed() {
        // Verify that the map container is displayed
        onView(withId(R.id.map)).check(matches(isDisplayed()))
    }
}
