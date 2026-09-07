package org.renpy.android

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.PopupWindow
import java.io.File
import java.lang.ref.WeakReference

/**
 * Encapsulated UI helpers for rendering desktop notification toasts on top of any active Activity.
 */
object DesktopNotificationUI {
    private var activePopupWindow: PopupWindow? = null
    private var activeContainer: LinearLayout? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    @Synchronized
    fun getOrCreateToastContainer(activity: Activity): ViewGroup {
        if (activity is LauncherActivity) {
            return activity.findViewById<ViewGroup>(R.id.toastContainer)
        }

        val currentActivity = currentActivityRef?.get()
        if (currentActivity != activity || activePopupWindow == null || activeContainer == null) {
            try {
                activePopupWindow?.dismiss()
            } catch (e: Exception) {}

            val container = LinearLayout(activity).apply {
                id = R.id.toastContainer
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM
            }

            val widthPx = (320 * activity.resources.displayMetrics.density).toInt()
            val popup = PopupWindow(
                container,
                widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
            ).apply {
                isClippingEnabled = false
                isTouchable = true
                isOutsideTouchable = true
            }

            activeContainer = container
            activePopupWindow = popup
            currentActivityRef = WeakReference(activity)
        }

        return activeContainer!!
    }

    fun showNotificationToast(activity: Activity, title: String, message: String, imagePath: String?) {
        activity.runOnUiThread {
            try {
                val container = getOrCreateToastContainer(activity)
                val inflater = LayoutInflater.from(activity)
                val toastView = inflater.inflate(R.layout.layout_notification_toast, container, false)
                
                toastView.findViewById<TextView>(R.id.toastTitle).text = title
                toastView.findViewById<TextView>(R.id.toastMessage).text = message

                val imgAvatar = toastView.findViewById<ImageView>(R.id.toastAvatar)
                if (!imagePath.isNullOrEmpty()) {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            imgAvatar.setImageBitmap(bitmap)
                        } else {
                            imgAvatar.setImageResource(R.drawable.ic_notifications)
                        }
                    } else {
                        imgAvatar.setImageResource(R.drawable.ic_notifications)
                    }
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_notifications)
                }

                toastView.findViewById<View>(R.id.toastClose).setOnClickListener {
                    slideOutAndRemoveToast(container, toastView)
                }

                container.addView(toastView)

                if (activity !is LauncherActivity) {
                    val popup = activePopupWindow
                    if (popup != null && !popup.isShowing) {
                        val root = activity.window.decorView
                        val xOffset = (16 * activity.resources.displayMetrics.density).toInt()
                        val yOffset = (8 * activity.resources.displayMetrics.density).toInt()
                        
                        popup.showAtLocation(
                            root,
                            android.view.Gravity.BOTTOM or android.view.Gravity.END,
                            xOffset,
                            yOffset
                        )
                    }
                } else {
                    container.bringToFront()
                }

                val density = activity.resources.displayMetrics.density
                toastView.translationX = 340f * density
                toastView.alpha = 0f
                toastView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // max 3
                if (container.childCount > 3) {
                    val oldest = container.getChildAt(0)
                    slideOutAndRemoveToast(container, oldest)
                }

                toastView.postDelayed({
                    if (toastView.parent != null) {
                        slideOutAndRemoveToast(container, toastView)
                    }
                }, 5000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun slideOutAndRemoveToast(container: ViewGroup, view: View) {
        val density = container.context.resources.displayMetrics.density
        view.animate()
            .translationX(340f * density)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                container.removeView(view)
                if (container == activeContainer && container.childCount == 0) {
                    try {
                        activePopupWindow?.dismiss()
                    } catch (e: Exception) {}
                    activePopupWindow = null
                    activeContainer = null
                }
            }
            .start()
    }
}
