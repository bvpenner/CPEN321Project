package com.example.cpen321app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationWorkerTest {

    @Test
    fun doWork_withLocationPermission_returnsSuccessOrRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<LocationWorker>(context).build()
        val result = worker.doWork()
        assertTrue(result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry())
    }
}
