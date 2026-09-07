package org.renpy.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.databinding.ItemToolboxToolBinding
import org.renpy.android.databinding.ToolboxPanelBinding
import org.renpy.android.databinding.ToolboxTriggerBinding

@SuppressLint("StaticFieldLeak")
object ToolboxManager {
    private const val TAG = "ToolboxManager"

    private var triggerView: View? = null
    private var panelView: View? = null

    data class ToolItem(
        val nameResId: Int,
        val onClick: (Context) -> Unit
    )

    @JvmStatic
    fun initialize(activity: PythonSDLActivity) {
        activity.runOnUiThread {
            if (triggerView != null) {
                val parent = triggerView?.parent as? ViewGroup
                if (parent == null) {
                    activity.mFrameLayout.addView(triggerView)
                }
                return@runOnUiThread
            }

            val inflater = LayoutInflater.from(activity)
            val binding = ToolboxTriggerBinding.inflate(inflater, activity.mFrameLayout, false)
            val view = binding.root
            triggerView = view

            view.isFocusable = false
            view.isFocusableInTouchMode = false
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                view.fitsSystemWindows = false
            } else {
                @Suppress("DEPRECATION")
                view.systemUiVisibility = activity.window.decorView.systemUiVisibility
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                view.post {
                    view.systemGestureExclusionRects = listOf(
                        android.graphics.Rect(0, 0, view.width, view.height)
                    )
                }
            }

            setupTriggerGesture(activity, view)

            activity.mFrameLayout.addView(view)
        }
    }

    private fun setupTriggerGesture(activity: PythonSDLActivity, view: View) {
        var startRawX = 0f
        var startY = 0f
        var isSwiping = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startY = event.y
                    isSwiping = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = Math.abs(event.y - startY)
                    if (dx > 40 && dx > dy && !isSwiping) {
                        isSwiping = true
                        showToolbox(activity)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - startRawX
                    val dy = Math.abs(event.y - startY)
                    if (Math.abs(dx) < 15 && dy < 15) {
                        showToolbox(activity)
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    @JvmStatic
    fun showToolbox(activity: PythonSDLActivity) {
        activity.runOnUiThread {
            if (panelView != null) return@runOnUiThread

            val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val darkMode = prefs.getBoolean("dark_mode_enabled", false)
            val language = prefs.getString("language", "English") ?: "English"
            val locale = when (language) {
                "Español" -> java.util.Locale("es")
                "Português" -> java.util.Locale("pt")
                else -> java.util.Locale.ENGLISH
            }

            val baseConfig = activity.resources.configuration
            val config = Configuration(baseConfig)
            config.setLocale(locale)
            config.uiMode = if (darkMode) {
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            } else {
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
            val themedContext = activity.createConfigurationContext(config)
            val contextThemeWrapper = android.view.ContextThemeWrapper(themedContext, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
            val themedInflater = LayoutInflater.from(contextThemeWrapper)

            val binding = ToolboxPanelBinding.inflate(themedInflater, activity.mFrameLayout, false)
            val view = binding.root
            panelView = view

            // prevent the overlay from taking focus and revealing system bars
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                view.fitsSystemWindows = false
            } else {
                @Suppress("DEPRECATION")
                view.systemUiVisibility = activity.window.decorView.systemUiVisibility
            }

            // reinforce immersive fullscreen mode when system bars are shown
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val statusVisible = windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())
                val navVisible = windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
                if (statusVisible || navVisible) {
                    activity.applyImmersiveFullscreen()
                }
                windowInsets
            }

            val toolboxDrawer = binding.toolboxDrawer
            val dimBackground = binding.toolboxDimBackground
            val recyclerView = binding.toolboxRecyclerview

            // exclude drawer from system gestures
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                toolboxDrawer.post {
                    toolboxDrawer.systemGestureExclusionRects = listOf(
                        android.graphics.Rect(0, 0, toolboxDrawer.width, toolboxDrawer.height)
                    )
                }
            }

            // prevent rendering flicker, set initial off-screen translation before adding view to FrameLayout
            toolboxDrawer.translationX = -activity.resources.displayMetrics.widthPixels.toFloat()

            ViewCompat.setOnApplyWindowInsetsListener(toolboxDrawer) { drawer, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                drawer.setPadding(
                    insets.left + 16.dpToPx(contextThemeWrapper),
                    drawer.paddingTop,
                    drawer.paddingRight,
                    drawer.paddingBottom
                )
                windowInsets
            }

            dimBackground.setOnClickListener {
                hideToolbox(activity)
            }

            val gestureDetector = GestureDetector(contextThemeWrapper, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 != null) {
                        val diffX = e2.x - e1.x
                        val diffY = e2.y - e1.y
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            if (diffX < -80 && Math.abs(velocityX) > 100) {
                                hideToolbox(activity)
                                return true
                            }
                        }
                    }
                    return false
                }
            })

            toolboxDrawer.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }

            val tools = getToolItems(activity)
            recyclerView.layoutManager = GridLayoutManager(contextThemeWrapper, 3)
            recyclerView.adapter = ToolsAdapter(contextThemeWrapper, tools)

            activity.mFrameLayout.addView(view)
            activity.applyImmersiveFullscreen()

            dimBackground.alpha = 0f
            dimBackground.animate().alpha(1f).setDuration(250).start()

            toolboxDrawer.post {
                val width = toolboxDrawer.width.toFloat()
                toolboxDrawer.translationX = -width
                toolboxDrawer.animate()
                    .translationX(0f)
                    .setDuration(250)
                    .start()
            }
        }
    }

    @JvmStatic
    fun hideToolbox(activity: PythonSDLActivity) {
        activity.runOnUiThread {
            val view = panelView ?: return@runOnUiThread
            val toolboxDrawer = view.findViewById<View>(R.id.toolbox_drawer) ?: return@runOnUiThread
            val dimBackground = view.findViewById<View>(R.id.toolbox_dim_background) ?: return@runOnUiThread

            dimBackground.animate().alpha(0f).setDuration(250).start()
            val width = toolboxDrawer.width.toFloat()
            toolboxDrawer.animate()
                .translationX(-width)
                .setDuration(250)
                .withEndAction {
                    activity.mFrameLayout.removeView(view)
                    panelView = null
                    activity.applyImmersiveFullscreen()
                }
                .start()
        }
    }

    private fun getToolItems(activity: PythonSDLActivity): List<ToolItem> {
        return listOf(
            ToolItem(R.string.tool_virtual_keyboard) { ctx ->
                hideToolbox(activity)
                VirtualKeyboardManager.showKeyboard(activity)
            },
            ToolItem(R.string.tool_window_controller) { ctx ->
                hideToolbox(activity)
                showWindowModeDialog(activity)
            }
        )
    }

    private fun showWindowModeDialog(activity: PythonSDLActivity) {
        val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val contextThemeWrapper = android.view.ContextThemeWrapper(activity, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        val currentMode = prefs.getString("renpy_window_mode", "fullscreen") ?: "fullscreen"
        val options = arrayOf(
            contextThemeWrapper.getString(R.string.renpy_window_mode_fullscreen),
            contextThemeWrapper.getString(R.string.renpy_window_mode_windowed)
        )
        val checkedItem = if (currentMode == "windowed") 1 else 0

        GameDialogBuilder(contextThemeWrapper)
            .setTitle(contextThemeWrapper.getString(R.string.renpy_window_mode_title))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newMode = if (which == 1) "windowed" else "fullscreen"
                if (newMode != currentMode) {
                    activity.windowDecorator?.setWindowModeDynamically(newMode == "windowed")
                }
                dialog.dismiss()
            }
            .setNegativeButton(contextThemeWrapper.getString(R.string.cancel), null)
            .show()
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private class ToolsAdapter(
        private val context: Context,
        private val items: List<ToolItem>
    ) : RecyclerView.Adapter<ToolsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemToolboxToolBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemToolboxToolBinding.inflate(LayoutInflater.from(context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.txtToolName.text = context.getString(item.nameResId)
            holder.binding.root.setOnClickListener {
                item.onClick(context)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
