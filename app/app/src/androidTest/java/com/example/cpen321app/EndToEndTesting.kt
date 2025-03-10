package com.example.cpen321app

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.toPackage
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.After

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.mockito.Mock
import org.mockito.Mockito

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTesting {

    @get:Rule
    val activityRule = IntentsTestRule(MainActivity::class.java, true, false)

    private lateinit var mockGetCredentialResponse: GetCredentialResponse
    private lateinit var mockCredential: Credential

    @Before
    fun setUp() {
//        // Initialize Intents Framework
//        Intents.init()
//
//        val dataBundle: Bundle = Bundle()
//
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_PROFILE_PICTURE_URI", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_DISPLAY_NAME", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_PHONE_NUMBER", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_GIVEN_NAME", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN", "")
//        dataBundle.putString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_FAMILY_NAME", "")
//
//        mockCredential = Mockito.mock(Credential::class.java).apply {
//            Mockito.`when`(this.type).thenReturn(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
//            Mockito.`when`(this.data).thenReturn(dataBundle)
//        }
//
//        mockGetCredentialResponse = Mockito.mock(GetCredentialResponse::class.java).apply {
//            Mockito.`when`(this.credential).thenReturn(mockCredential)
//        }
//
//        val credentialManager = Mockito.mock(CredentialManager::class.java)
//        Mockito.`when`(credentialManager.getCredential(Mockito.any() as Context, Mockito.any() as GetCredentialRequest))
//            .thenReturn(mockGetCredentialResponse)
//
//

    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.cpen321app", appContext.packageName)
    }

    @Test
    fun testManageTask() {

    }

    private fun testAddTask() {

    }

    private fun testDeleteTask() {

    }

    private fun testUpdateTask() {

    }

}





//@RunWith(AndroidJUnit4::class)
//@LargeTest
//class HelloWorldEspressoTest {
//
//    @get:Rule
//    val activityRule = ActivityScenarioRule(MainActivity::class.java)
//
//    @Test fun listGoesOverTheFold() {
//        onView(withText("Hello world!")).check(matches(isDisplayed()))
//    }
//}