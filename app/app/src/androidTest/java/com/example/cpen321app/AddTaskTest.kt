package com.example.cpen321app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.junit.runner.RunWith
import java.lang.reflect.Field

@RunWith(AndroidJUnit4::class)
class AddTaskTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AddTask::class.java)

    private lateinit var mockTaskViewModel: TaskViewModel

    @Before
    fun setUp() {
        Intents.init()
        mockTaskViewModel = mock(TaskViewModel::class.java)
        activityRule.scenario.onActivity { activity ->
            val field: Field = AddTask::class.java.getDeclaredField("taskViewModel")
            field.isAccessible = true
            field.set(activity, mockTaskViewModel)
        }
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun validForm_save_callsAddTask() {
        onView(withId(R.id.editTextName))
            .perform(typeText("Test Task"), closeSoftKeyboard())
        onView(withId(R.id.editText_description))
            .perform(typeText("This is a description for the test task that is longer than 10 characters"), closeSoftKeyboard())
        onView(withId(R.id.editText_taskStart))
            .perform(click())
        TestUtils.simulateTimePickerConfirmation("10:00")
        onView(withId(R.id.editText_taskEnd))
            .perform(click())
        TestUtils.simulateTimePickerConfirmation("11:00")
        onView(withId(R.id.editText_duration))
            .perform(replaceText("60"), closeSoftKeyboard())
        onView(withId(R.id.editText_taskLat))
            .perform(replaceText("37.422"), closeSoftKeyboard())
        onView(withId(R.id.editText_taskLng))
            .perform(replaceText("-122.084"), closeSoftKeyboard())
        onView(withId(R.id.editText_taskPrio))
            .perform(replaceText("1"), closeSoftKeyboard())
        onView(withId(R.id.button_taskCreate)).perform(click())
        verify(mockTaskViewModel).addTask(any())
    }

    @Test
    fun unsavedChanges_pressBack_showsDiscardDialog() {
        onView(withId(R.id.editTextName))
            .perform(typeText("Unsaved Task"), closeSoftKeyboard())
        pressBack()
        onView(withText("Discard Changes?")).check(matches(isDisplayed()))
    }
}
