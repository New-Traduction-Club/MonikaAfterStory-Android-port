package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WindowSystemTest {

    @Test
    fun testDesktopWindowManagerBroadcasts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        var receivedId: String? = null
        var receivedState: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED) {
                    receivedId = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_ID)
                    receivedState = intent.getStringExtra(DesktopWindowManager.EXTRA_STATE)
                    latch.countDown()
                }
            }
        }

        val filter = IntentFilter(DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            DesktopWindowManager.notifyStateChanged(context, "test_id", "Test App", "RUNNING")
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("test_id", receivedId)
            assertEquals("RUNNING", receivedState)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testDesktopWindowManagerActiveWindowName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DesktopWindowManager.registerReceiver(context)
        try {
            // Active window should default to "Desktop"
            assertEquals("Desktop", DesktopWindowManager.getActiveWindowName())

            // Simulate state RUNNING
            DesktopWindowManager.notifyStateChanged(context, "activity_1", "Test Activity", "RUNNING")
            SystemClock.sleep(200)
            assertEquals("Test Activity", DesktopWindowManager.getActiveWindowName())

            // Simulate "Another Activity" starting
            DesktopWindowManager.notifyStateChanged(context, "activity_2", "Another Activity", "RUNNING")
            SystemClock.sleep(200)
            assertEquals("Another Activity", DesktopWindowManager.getActiveWindowName())

            // Simulate "Another Activity" minimizing
            DesktopWindowManager.notifyStateChanged(context, "activity_2", "Another Activity", "MINIMIZED")
            SystemClock.sleep(200)
            assertEquals("Test Activity", DesktopWindowManager.getActiveWindowName())

            // Simulate "Test Activity" being destroyed
            DesktopWindowManager.notifyStateChanged(context, "activity_1", "Test Activity", "DESTROYED")
            SystemClock.sleep(200)
            assertEquals("Desktop", DesktopWindowManager.getActiveWindowName())
        } finally {
            DesktopWindowManager.unregisterReceiver(context)
        }
    }

    @Test
    fun testMinimizeAndRestoreCommands() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isWindowMinimizedState)
            }

            DesktopWindowManager.sendCommand(context, TestActivity::class.java.name, "MINIMIZE")
            
            SystemClock.sleep(500)

            scenario.onActivity { activity ->
                assertTrue(activity.isWindowMinimizedState)
                val card = activity.findViewById<View>(R.id.cardWindowContainer)
                assertEquals(0f, card.alpha, 0.01f)
                assertEquals(0.1f, card.scaleX, 0.01f)
                assertEquals(0.1f, card.scaleY, 0.01f)
            }

            DesktopWindowManager.sendCommand(context, TestActivity::class.java.name, "RESTORE")
            
            SystemClock.sleep(500)

            scenario.onActivity { activity ->
                assertFalse(activity.isWindowMinimizedState)
                val card = activity.findViewById<View>(R.id.cardWindowContainer)
                assertEquals(1f, card.alpha, 0.01f)
                assertEquals(1f, card.scaleX, 0.01f)
                assertEquals(1f, card.scaleY, 0.01f)
                activity.finish()
            }
        }
    }

    @Test
    fun testDragBoundaryConstraints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val headerView = activity.findViewById<View>(R.id.headerLayout)
                assertNotNull("Header layout must be present", headerView)

                val displayMetrics = activity.resources.displayMetrics
                val halfWidth = displayMetrics.widthPixels / 2
                val halfHeight = displayMetrics.heightPixels / 2

                val downTime = SystemClock.uptimeMillis()
                val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                headerView.dispatchTouchEvent(downEvent)
                downEvent.recycle()

                val moveTime = SystemClock.uptimeMillis()
                val moveEvent = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, 10000f, 10000f, 0)
                headerView.dispatchTouchEvent(moveEvent)
                moveEvent.recycle()

                val w = activity.window
                assertNotNull(w)
                val params = w.attributes

                assertEquals("x should be clamped to half screen width", halfWidth, params.x)
                assertEquals("y should be clamped to half screen height", halfHeight, params.y)

                val downEvent2 = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                headerView.dispatchTouchEvent(downEvent2)
                downEvent2.recycle()

                val moveEvent2 = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, -20000f, -20000f, 0)
                headerView.dispatchTouchEvent(moveEvent2)
                moveEvent2.recycle()

                val params2 = w.attributes
                assertEquals("x should be clamped to -half screen width", -halfWidth, params2.x)

                val windowHeight = if (params2.height > 0) params2.height else displayMetrics.heightPixels
                val expectedMinY = (windowHeight / 2) - halfHeight
                assertEquals("y should be clamped to minY to keep title bar on-screen", expectedMinY, params2.y)
                
                activity.finish()
            }
        }
    }

    @Test
    fun testWindowClampingOnConfigurationChange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val displayMetrics = activity.resources.displayMetrics
                val halfWidth = displayMetrics.widthPixels / 2
                val halfHeight = displayMetrics.heightPixels / 2

                val fieldX = GameWindowActivity::class.java.getDeclaredField("lastWindowX")
                fieldX.isAccessible = true
                val fieldY = GameWindowActivity::class.java.getDeclaredField("lastWindowY")
                fieldY.isAccessible = true

                fieldX.set(activity, 99999)
                fieldY.set(activity, 99999)

                val fieldRoot = GameWindowActivity::class.java.getDeclaredField("windowRootLayout")
                fieldRoot.isAccessible = true
                val rootLayout = fieldRoot.get(activity) as android.view.ViewGroup

                val methodApply = GameWindowActivity::class.java.getDeclaredMethod("applyWindowMode", android.view.ViewGroup::class.java)
                methodApply.isAccessible = true
                methodApply.invoke(activity, rootLayout)

                val clampedX = fieldX.get(activity) as Int
                val clampedY = fieldY.get(activity) as Int

                assertTrue("x must be clamped to halfScreenWidth: $clampedX <= $halfWidth", clampedX <= halfWidth)
                assertTrue("y must be clamped to halfScreenHeight: $clampedY <= $halfHeight", clampedY <= halfHeight)
                
                activity.finish()
            }
        }
    }

    @Test
    fun testFileExplorerDirectoryPreservation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startDir = context.filesDir.absolutePath
        
        // Create a subfolder to navigate to
        val subDir = java.io.File(context.filesDir, "test_subdir")
        if (!subDir.exists()) {
            subDir.mkdirs()
        }

        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        val scenario = ActivityScenario.launch(TestFileExplorerActivity::class.java)
        try {
            // Initialize rootDir and load the subfolder
            scenario.onActivity { activity ->
                val rootDirField = FileExplorerActivity::class.java.getDeclaredField("rootDir")
                rootDirField.isAccessible = true
                rootDirField.set(activity, java.io.File(startDir))

                val delegateField = FileExplorerActivity::class.java.getDeclaredField("viewModel\$delegate")
                delegateField.isAccessible = true
                val lazyValue = delegateField.get(activity) as Lazy<*>
                val viewModel = lazyValue.value as FileExplorerViewModel
                
                viewModel.loadDirectory(subDir.absolutePath)
            }

            // Sleep to let LiveData update
            SystemClock.sleep(200)

            // Verify navigate to subfolder worked
            scenario.onActivity { activity ->
                val delegateField = FileExplorerActivity::class.java.getDeclaredField("viewModel\$delegate")
                delegateField.isAccessible = true
                val lazyValue = delegateField.get(activity) as Lazy<*>
                val viewModel = lazyValue.value as FileExplorerViewModel

                assertEquals(subDir.absolutePath, viewModel.currentDir.value?.absolutePath)

                // Call onNewIntent simulating restore
                val restoreIntent = Intent(context, TestFileExplorerActivity::class.java)
                activity.callOnNewIntent(restoreIntent)
            }

            // Sleep again
            SystemClock.sleep(200)

            // Verify current directory is preserved
            scenario.onActivity { activity ->
                val delegateField = FileExplorerActivity::class.java.getDeclaredField("viewModel\$delegate")
                delegateField.isAccessible = true
                val lazyValue = delegateField.get(activity) as Lazy<*>
                val viewModel = lazyValue.value as FileExplorerViewModel

                assertEquals(subDir.absolutePath, viewModel.currentDir.value?.absolutePath)

                // Call onNewIntent simulating shortcut reset
                val resetIntent = Intent(context, TestFileExplorerActivity::class.java).apply {
                    putExtra("startPath", startDir)
                }
                activity.callOnNewIntent(resetIntent)
            }

            // Sleep again
            SystemClock.sleep(200)

            // Verify it resets
            scenario.onActivity { activity ->
                val delegateField = FileExplorerActivity::class.java.getDeclaredField("viewModel\$delegate")
                delegateField.isAccessible = true
                val lazyValue = delegateField.get(activity) as Lazy<*>
                val viewModel = lazyValue.value as FileExplorerViewModel

                assertEquals(startDir, viewModel.currentDir.value?.absolutePath)
                activity.finish()
            }
        } finally {
            try {
                scenario.close()
            } catch (e: AssertionError) {
                // Suppress destruction timeout assertion error
            }
        }
        subDir.delete()
    }

    @Test
    fun testDesktopNotifications() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Clear previous notification history
        NotificationHistoryManager.clearAll(context)
        assertEquals(0, NotificationHistoryManager.getUnreadCount(context))

        // Pre-set window mode preference to prevent setup dialog
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        appPrefs.edit()
            .putBoolean("is_setup_completed", true)
            .putBoolean("is_first_launch", false)
            .putBoolean("setup_language_confirmed", true)
            .apply()

        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            SystemClock.sleep(200)

            scenario.onActivity { activity ->
                // Verify initial tray state
                val badge = activity.findViewById<TextView>(R.id.txtNotificationBadge)
                assertEquals(View.GONE, badge.visibility)

                val emptyText = activity.findViewById<TextView>(R.id.txtNoNotifications)
                val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
                val panel = activity.findViewById<View>(R.id.notificationCenterPanel)
                
                assertEquals(View.GONE, panel.visibility)
            }

            // Trigger a desktop notification via JNI bridge
            NotificationWorker.showDesktopNotification(context, "Monika", "Dummy message", null)
            SystemClock.sleep(400) // Allow receiver to process broadcast

            scenario.onActivity { activity ->
                // Verify badge is updated to 1
                val badge = activity.findViewById<TextView>(R.id.txtNotificationBadge)
                assertEquals(View.VISIBLE, badge.visibility)
                assertEquals("1", badge.text.toString())

                // Verify toast container has 1 child
                val toastContainer = activity.findViewById<android.widget.LinearLayout>(R.id.toastContainer)
                assertEquals(1, toastContainer.childCount)

                val toastView = toastContainer.getChildAt(0)
                val titleView = toastView.findViewById<TextView>(R.id.toastTitle)
                val msgView = toastView.findViewById<TextView>(R.id.toastMessage)
                assertEquals("Monika", titleView.text.toString())
                assertEquals("Dummy message", msgView.text.toString())
            }

            // Trigger 2 more notifications to test stack and badge
            NotificationWorker.showDesktopNotification(context, "Monika", "Second message", null)
            NotificationWorker.showDesktopNotification(context, "Monika", "Third message", null)
            SystemClock.sleep(400)

            scenario.onActivity { activity ->
                val badge = activity.findViewById<TextView>(R.id.txtNotificationBadge)
                assertEquals("3", badge.text.toString())

                val toastContainer = activity.findViewById<android.widget.LinearLayout>(R.id.toastContainer)
                // Stacking limit is max 3
                assertTrue(toastContainer.childCount <= 3)

                // Toggle Notification Center Panel (Click the tray button)
                val btnCenter = activity.findViewById<View>(R.id.btnNotificationCenter)
                btnCenter.performClick()
            }

            SystemClock.sleep(300) // Allow panel animations to run

            scenario.onActivity { activity ->
                val panel = activity.findViewById<View>(R.id.notificationCenterPanel)
                assertEquals(View.VISIBLE, panel.visibility)

                // Verify badge count is reset/hidden since panel is opened (marked read)
                val badge = activity.findViewById<TextView>(R.id.txtNotificationBadge)
                assertEquals(View.GONE, badge.visibility)

                // Verify list has 3 items
                val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
                assertEquals(View.VISIBLE, rv.visibility)
                assertNotNull(rv.adapter)
                assertEquals(3, rv.adapter?.itemCount)

                // Clear all notifications
                val btnClearAll = activity.findViewById<View>(R.id.btnClearAllNotifications)
                btnClearAll.performClick()
            }

            SystemClock.sleep(200)

            scenario.onActivity { activity ->
                // Verify list is empty and placeholder is visible
                val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
                assertEquals(View.GONE, rv.visibility)
                
                val emptyText = activity.findViewById<TextView>(R.id.txtNoNotifications)
                assertEquals(View.VISIBLE, emptyText.visibility)
                
                activity.finish()
            }
        }
    }
}
