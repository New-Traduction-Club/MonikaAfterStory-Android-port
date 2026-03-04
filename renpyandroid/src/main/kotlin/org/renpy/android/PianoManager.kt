package org.renpy.android

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.util.Log
import android.widget.SeekBar
import android.graphics.Rect
import android.view.ViewGroup
import android.content.Context

object PianoManager {

    private var pianoView: View? = null
    private val activePointers = mutableMapOf<Int, Int>() // pointerId -> keyCode
    private val pressedKeys = mutableSetOf<Int>() // keyCodes currently pressed

    @JvmStatic
    fun showPianoKeyboard() {
        val activity = PythonSDLActivity.mActivity ?: return
        
        activity.runOnUiThread {
            if (pianoView != null) return@runOnUiThread

            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.piano_keyboard, activity.mFrameLayout, false)
            pianoView = view
            
            setupSettings(view, activity)
            setupTouchSliding(view, activity)
            
            activity.mFrameLayout.addView(view)
        }
    }

    @JvmStatic
    fun hidePianoKeyboard() {
        val activity = PythonSDLActivity.mActivity ?: return
        
        activity.runOnUiThread {
            pianoView?.let {
                activity.mFrameLayout.removeView(it)
                pianoView = null
                
                // Release all keys
                for (code in pressedKeys) {
                    injectKeyEvent(activity, KeyEvent.ACTION_UP, code)
                }
                pressedKeys.clear()
                activePointers.clear()
            }
        }
    }

    private fun setupSettings(view: View, activity: PythonSDLActivity) {
        val btnSettings = view.findViewById<Button>(R.id.btn_piano_settings)
        val btnCloseSettings = view.findViewById<Button>(R.id.btn_piano_close_settings)
        val settingsArea = view.findViewById<LinearLayout>(R.id.piano_settings_area)
        val keyboardArea = view.findViewById<View>(R.id.piano_keyboard_area)
        val seekBar = view.findViewById<SeekBar>(R.id.piano_size_seekbar)
        val opacitySeekBar = view.findViewById<SeekBar>(R.id.piano_opacity_seekbar)

        val prefs = activity.getSharedPreferences("PianoSettings", Context.MODE_PRIVATE)
        val savedSize = prefs.getInt("size", 50)
        val savedOpacity = prefs.getInt("opacity", 100)

        // Set initial visual values based on preferences
        keyboardArea.scaleX = 0.5f + (savedSize / 100f)
        keyboardArea.scaleY = 0.5f + (savedSize / 100f)
        // Opacity mapping: progress 0-100 to alpha 0.15f-1.0f
        keyboardArea.alpha = 0.15f + (savedOpacity / 100f) * 0.85f

        seekBar.progress = savedSize
        opacitySeekBar.progress = savedOpacity

        btnSettings.setOnClickListener {
            // Cancel all active pointers before hiding
            for (code in pressedKeys) {
                injectKeyEvent(activity, KeyEvent.ACTION_UP, code)
                setKeyVisualState(view, code, false)
            }
            pressedKeys.clear()
            activePointers.clear()

            keyboardArea.visibility = View.GONE
            btnSettings.visibility = View.GONE
            settingsArea.visibility = View.VISIBLE
        }

        btnCloseSettings.setOnClickListener {
            settingsArea.visibility = View.GONE
            keyboardArea.visibility = View.VISIBLE
            btnSettings.visibility = View.VISIBLE
        }

        // Set pivot to bottom center so the keyboard grows upwards and from the middle
        keyboardArea.post {
            keyboardArea.pivotX = keyboardArea.width / 2f
            keyboardArea.pivotY = keyboardArea.height.toFloat()
        }

        // SeekBar scaling (0 to 100) -> scale 0.5 to 1.5
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.5f + (progress / 100f)
                keyboardArea.scaleX = scale
                keyboardArea.scaleY = scale
                prefs.edit().putInt("size", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // SeekBar opacity (0 to 100) -> alpha 0.15 to 1.0
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = 0.15f + (progress / 100f) * 0.85f
                keyboardArea.alpha = alpha
                prefs.edit().putInt("opacity", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTouchSliding(view: View, activity: PythonSDLActivity) {
        val keyboardArea = view.findViewById<ViewGroup>(R.id.piano_keyboard_area)
        
        val whiteKeyMap = mapOf(
            view.findViewById<View>(R.id.btn_white_q) to KeyEvent.KEYCODE_Q,
            view.findViewById<View>(R.id.btn_white_w) to KeyEvent.KEYCODE_W,
            view.findViewById<View>(R.id.btn_white_e) to KeyEvent.KEYCODE_E,
            view.findViewById<View>(R.id.btn_white_r) to KeyEvent.KEYCODE_R,
            view.findViewById<View>(R.id.btn_white_t) to KeyEvent.KEYCODE_T,
            view.findViewById<View>(R.id.btn_white_y) to KeyEvent.KEYCODE_Y,
            view.findViewById<View>(R.id.btn_white_u) to KeyEvent.KEYCODE_U,
            view.findViewById<View>(R.id.btn_white_i) to KeyEvent.KEYCODE_I,
            view.findViewById<View>(R.id.btn_white_o) to KeyEvent.KEYCODE_O,
            view.findViewById<View>(R.id.btn_white_p) to KeyEvent.KEYCODE_P,
            view.findViewById<View>(R.id.btn_white_lbracket) to KeyEvent.KEYCODE_LEFT_BRACKET,
            view.findViewById<View>(R.id.btn_white_rbracket) to KeyEvent.KEYCODE_RIGHT_BRACKET
        )
        
        val blackKeyMap = mapOf(
            view.findViewById<View>(R.id.btn_black_2) to KeyEvent.KEYCODE_2,
            view.findViewById<View>(R.id.btn_black_3) to KeyEvent.KEYCODE_3,
            view.findViewById<View>(R.id.btn_black_4) to KeyEvent.KEYCODE_4,
            view.findViewById<View>(R.id.btn_black_6) to KeyEvent.KEYCODE_6,
            view.findViewById<View>(R.id.btn_black_7) to KeyEvent.KEYCODE_7,
            view.findViewById<View>(R.id.btn_black_9) to KeyEvent.KEYCODE_9,
            view.findViewById<View>(R.id.btn_black_0) to KeyEvent.KEYCODE_0,
            view.findViewById<View>(R.id.btn_black_minus) to KeyEvent.KEYCODE_MINUS
        )
        
        for (key in whiteKeyMap.keys + blackKeyMap.keys) {
            key.isClickable = false
            key.isFocusable = false
        }
        
        keyboardArea.setOnTouchListener { _, event ->
            val scale = keyboardArea.scaleX // assume uniform scaling
            
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    val rawX = event.getX(pointerIndex)
                    val rawY = event.getY(pointerIndex)
                    
                    val keyCode = findKeyAt(keyboardArea, rawX, rawY, blackKeyMap, whiteKeyMap)
                    if (keyCode != null) {
                        activePointers[pointerId] = keyCode
                        if (!pressedKeys.contains(keyCode)) {
                            pressedKeys.add(keyCode)
                            injectKeyEvent(activity, KeyEvent.ACTION_DOWN, keyCode)
                            setKeyVisualState(view, keyCode, true)
                        }
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val currentFrameKeys = mutableSetOf<Int>()
                    
                    for (i in 0 until event.pointerCount) {
                        val pointerId = event.getPointerId(i)
                        val rawX = event.getX(i)
                        val rawY = event.getY(i)
                        
                        val newKeyCode = findKeyAt(keyboardArea, rawX, rawY, blackKeyMap, whiteKeyMap)
                        val oldKeyCode = activePointers[pointerId]
                        if (newKeyCode != null) {
                            currentFrameKeys.add(newKeyCode)
                        }
                        
                        if (newKeyCode != oldKeyCode) {
                            // Touch moved to a different key
                            if (oldKeyCode != null) {
                                activePointers.remove(pointerId)
                            }
                            if (newKeyCode != null) {
                                activePointers[pointerId] = newKeyCode
                                if (!pressedKeys.contains(newKeyCode)) {
                                    pressedKeys.add(newKeyCode)
                                    injectKeyEvent(activity, KeyEvent.ACTION_DOWN, newKeyCode)
                                    setKeyVisualState(view, newKeyCode, true)
                                }
                            }
                        }
                    }
                    
                    // Release any key that is no longer being actively touched by any pointer
                    val keysToRelease = pressedKeys.filter { !currentFrameKeys.contains(it) }
                    for (keyCode in keysToRelease) {
                        pressedKeys.remove(keyCode)
                        injectKeyEvent(activity, KeyEvent.ACTION_UP, keyCode)
                        setKeyVisualState(view, keyCode, false)
                    }
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    
                    val keyCode = activePointers.remove(pointerId)
                    if (keyCode != null) {
                        // Only release if no other pointer is still holding this key
                        if (!activePointers.containsValue(keyCode)) {
                            pressedKeys.remove(keyCode)
                            injectKeyEvent(activity, KeyEvent.ACTION_UP, keyCode)
                            setKeyVisualState(view, keyCode, false)
                        }
                    }
                    
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) {
                        // Failsafe cleanup
                        for (code in pressedKeys) {
                            injectKeyEvent(activity, KeyEvent.ACTION_UP, code)
                            setKeyVisualState(view, code, false)
                        }
                        pressedKeys.clear()
                        activePointers.clear()
                    }
                }
            }
            true // consume touch
        }
    }

    private fun findKeyAt(keyboardArea: ViewGroup, x: Float, y: Float, blackKeyMap: Map<View, Int>, whiteKeyMap: Map<View, Int>): Int? {
        val rect = Rect()
        for ((view, code) in blackKeyMap) {
            if (isPointInside(keyboardArea, view, x, y, rect)) return code
        }
        for ((view, code) in whiteKeyMap) {
            if (isPointInside(keyboardArea, view, x, y, rect)) return code
        }
        return null
    }

    private fun isPointInside(keyboardArea: ViewGroup, view: View, x: Float, y: Float, rect: Rect): Boolean {
        if (view.visibility != View.VISIBLE) return false
        view.getDrawingRect(rect)
        keyboardArea.offsetDescendantRectToMyCoords(view, rect)
        
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }

    private fun setKeyVisualState(view: View, keyCode: Int, isPressed: Boolean) {
        val id = when (keyCode) {
            KeyEvent.KEYCODE_Q -> R.id.btn_white_q
            KeyEvent.KEYCODE_W -> R.id.btn_white_w
            KeyEvent.KEYCODE_E -> R.id.btn_white_e
            KeyEvent.KEYCODE_R -> R.id.btn_white_r
            KeyEvent.KEYCODE_T -> R.id.btn_white_t
            KeyEvent.KEYCODE_Y -> R.id.btn_white_y
            KeyEvent.KEYCODE_U -> R.id.btn_white_u
            KeyEvent.KEYCODE_I -> R.id.btn_white_i
            KeyEvent.KEYCODE_O -> R.id.btn_white_o
            KeyEvent.KEYCODE_P -> R.id.btn_white_p
            KeyEvent.KEYCODE_LEFT_BRACKET -> R.id.btn_white_lbracket
            KeyEvent.KEYCODE_RIGHT_BRACKET -> R.id.btn_white_rbracket
            
            KeyEvent.KEYCODE_2 -> R.id.btn_black_2
            KeyEvent.KEYCODE_3 -> R.id.btn_black_3
            KeyEvent.KEYCODE_4 -> R.id.btn_black_4
            KeyEvent.KEYCODE_6 -> R.id.btn_black_6
            KeyEvent.KEYCODE_7 -> R.id.btn_black_7
            KeyEvent.KEYCODE_9 -> R.id.btn_black_9
            KeyEvent.KEYCODE_0 -> R.id.btn_black_0
            KeyEvent.KEYCODE_MINUS -> R.id.btn_black_minus
            else -> null
        }
        
        id?.let {
            val keyView = view.findViewById<View>(it)
            keyView?.isPressed = isPressed
        }
    }

    private fun injectKeyEvent(activity: PythonSDLActivity, action: Int, keyCode: Int) {
        val event = KeyEvent(action, keyCode)
        activity.dispatchKeyEvent(event)
    }
}
