package com.example.cpen321app

import FirebaseMessagingService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseMessagingServiceTest {

    @Test
    fun testSendNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = FirebaseMessagingService()
        service.sendNotification(context, "Test Notification Message")
    }
}
