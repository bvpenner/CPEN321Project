package com.example.cpen321app

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapViewFragmentTest {

    @Test
    fun mapViewFragment_inflatesLayout() {
        // Launch MapViewFragment
        launchFragmentInContainer<MapViewFragment>()
        // Check that the ImageView (with id "imageView") in the layout is displayed
        onView(withId(R.id.imageView)).check(matches(isDisplayed()))
    }
}
