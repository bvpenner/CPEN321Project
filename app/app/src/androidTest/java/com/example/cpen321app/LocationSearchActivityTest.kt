package com.example.cpen321app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationSearchActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LocationSearchActivity::class.java)

    @Test
    fun locationSearchActivity_inflatesLayout() {
        // Launch the activity (the rule does this automatically)
        onView(withId(R.id.autocomplete_fragment)).check(matches(isDisplayed()))
    }

    @Test
    fun enterSearchText_updatesQuery() {
        // Launch the activity explicitly if needed
        ActivityScenario.launch(LocationSearchActivity::class.java)
        // Assuming the SearchView has a hint "Search a place"
        onView(withHint("Search a place"))
            .perform(typeText("Starbucks"), closeSoftKeyboard())
            .check(matches(withText("Starbucks")))
    }
}
