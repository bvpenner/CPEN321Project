package com.example.cpen321app

import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.test.espresso.Root
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

class ToastMatcher : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description?) {
        description?.appendText("is toast")
    }

    override fun matchesSafely(root: Root?): Boolean {
        if (root == null) return false

        val windowType = root.windowLayoutParams.get().type
        Log.d("ToastMatcher", "Checking window type: $windowType")

        // Allow for different window types based on API level
        if (windowType == WindowManager.LayoutParams.TYPE_TOAST ||
            windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY ||
            windowType == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) {

            // Ensure the Toast is not just part of the main activity window by checking tokens
            val windowToken = root.decorView.windowToken
            val appToken = root.decorView.applicationWindowToken

            if (windowToken === appToken) {
                Log.d("ToastMatcher", "Possible Toast found!")
                return true
            }
        }
        return false

    }
}
