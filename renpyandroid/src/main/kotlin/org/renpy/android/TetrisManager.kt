package org.renpy.android

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.content.Context
import android.util.Log

@SuppressLint("StaticFieldLeak")
object TetrisManager {
    private const val TAG = "TetrisManager"
    private var tetrisView: View? = null

    @JvmStatic
    fun showTetrisOverlay() {
        val activity = PythonSDLActivity.mActivity ?: return
        
        activity.runOnUiThread {
            if (tetrisView != null) return@runOnUiThread

            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.tetris_overlay, activity.mFrameLayout, false)
            tetrisView = view

            setupButtonListeners(view, activity)

            activity.mFrameLayout.addView(view)
            Log.d(TAG, "Tetris overlay shown")
        }
    }

    @JvmStatic
    fun hideTetrisOverlay() {
        val activity = PythonSDLActivity.mActivity ?: return

        activity.runOnUiThread {
            tetrisView?.let {
                activity.mFrameLayout.removeView(it)
                tetrisView = null
                Log.d(TAG, "Tetris overlay hidden")
            }
        }
    }

    private fun setupButtonListeners(view: View, activity: PythonSDLActivity) {
        // Map button IDs to KeyCodes
        val buttonMap = mapOf(
            R.id.btn_tetris_rotate to KeyEvent.KEYCODE_DPAD_UP,
            R.id.btn_tetris_left to KeyEvent.KEYCODE_DPAD_LEFT,
            R.id.btn_tetris_right to KeyEvent.KEYCODE_DPAD_RIGHT,
            R.id.btn_tetris_down to KeyEvent.KEYCODE_DPAD_DOWN,
            R.id.btn_tetris_hard_drop to KeyEvent.KEYCODE_SPACE,
            R.id.btn_tetris_hold to KeyEvent.KEYCODE_Q,
            R.id.btn_tetris_swap to KeyEvent.KEYCODE_E
        )

        for ((buttonId, keyCode) in buttonMap) {
            val button = view.findViewById<View>(buttonId)
            button?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        injectKeyEvent(activity, KeyEvent.ACTION_DOWN, keyCode)
                        v.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        injectKeyEvent(activity, KeyEvent.ACTION_UP, keyCode)
                        v.isPressed = false
                    }
                }
                true // consume the event
            }
        }
    }

    private fun injectKeyEvent(activity: PythonSDLActivity, action: Int, keyCode: Int) {
        val event = KeyEvent(action, keyCode)
        activity.dispatchKeyEvent(event)
    }
}
