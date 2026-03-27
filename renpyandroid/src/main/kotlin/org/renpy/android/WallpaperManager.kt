package org.renpy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Manages user-wallpapers stored in external storage.
 */
object WallpaperManager {

    const val MIN_SLIDESHOW_SELECTION = 2
    const val MAX_SLIDESHOW_SELECTION = 5

    private const val PREFS_KEY = "active_wallpaper"
    private const val DEFAULT_ID = "default"
    private const val WALLPAPERS_DIR = "wallpapers"
    private const val KEY_SLIDESHOW_ENABLED = "wallpaper_slideshow_enabled"
    private const val KEY_SLIDESHOW_INTERVAL_MINUTES = "wallpaper_slideshow_interval_minutes"
    private const val KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE = "wallpaper_slideshow_change_on_app_toggle"
    private const val KEY_SLIDESHOW_SELECTION = "wallpaper_slideshow_selection"
    private const val KEY_SLIDESHOW_LAST_CHANGE = "wallpaper_slideshow_last_change"

    data class SlideshowConfig(
        val enabled: Boolean,
        val intervalMinutes: Int?,
        val changeOnAppToggle: Boolean,
        val selectedIds: List<String>
    )

    private fun prefs(context: Context) = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private fun getWallpapersDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), WALLPAPERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getActiveId(context: Context): String {
        return prefs(context).getString(PREFS_KEY, DEFAULT_ID) ?: DEFAULT_ID
    }

    fun setActive(context: Context, id: String) {
        prefs(context)
            .edit()
            .putString(PREFS_KEY, id)
            .putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis())
            .apply()
    }

    fun getWallpaperList(context: Context): List<String> {
        val list = mutableListOf(DEFAULT_ID)
        val dir = getWallpapersDir(context)
        dir.listFiles()?.filter {
            it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
        }?.sortedBy { it.lastModified() }?.forEach {
            list.add(it.name)
        }
        return list
    }

    fun saveWallpaper(context: Context, bitmap: Bitmap, name: String): String {
        val dir = getWallpapersDir(context)
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return name
    }

    fun deleteWallpaper(context: Context, name: String): Boolean {
        if (name == DEFAULT_ID) return false
        val file = File(getWallpapersDir(context), name)
        val deleted = file.delete()
        if (deleted && getActiveId(context) == name) {
            setActive(context, DEFAULT_ID)
        }
        return deleted
    }

    fun getWallpaperFile(context: Context, name: String): File {
        return File(getWallpapersDir(context), name)
    }

    fun loadThumbnail(context: Context, name: String, targetWidth: Int): Bitmap? {
        val file = File(getWallpapersDir(context), name)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val sampleSize = Math.max(1, options.outWidth / targetWidth)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    fun applyWallpaper(context: Context, rootView: View) {
        val activeId = getActiveId(context)
        if (activeId == DEFAULT_ID) {
            rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
        } else {
            val file = File(getWallpapersDir(context), activeId)
            if (file.exists()) {
                val bitmap = decodeSampledBitmap(file, 1920, 1080)
                if (bitmap != null) {
                    rootView.background = BitmapDrawable(context.resources, bitmap)
                } else {
                    rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
                }
            } else {
                setActive(context, DEFAULT_ID)
                rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
            }
        }
    }

    fun getSlideshowConfig(context: Context): SlideshowConfig {
        val prefs = prefs(context)
        val enabled = prefs.getBoolean(KEY_SLIDESHOW_ENABLED, false)
        val interval = if (prefs.contains(KEY_SLIDESHOW_INTERVAL_MINUTES)) {
            prefs.getInt(KEY_SLIDESHOW_INTERVAL_MINUTES, 0).takeIf { it > 0 }
        } else null
        val changeOnToggle = prefs.getBoolean(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE, false)
        val rawSelection = prefs.getString(KEY_SLIDESHOW_SELECTION, "") ?: ""
        val selection = rawSelection.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val validSelection = sanitizeSelection(context, selection)
        val validatedEnabled = enabled && validSelection.size >= MIN_SLIDESHOW_SELECTION
        return SlideshowConfig(
            enabled = validatedEnabled,
            intervalMinutes = interval,
            changeOnAppToggle = changeOnToggle,
            selectedIds = validSelection
        )
    }

    fun saveSlideshowConfig(context: Context, config: SlideshowConfig) {
        val prefs = prefs(context)
        val selection = sanitizeSelection(context, config.selectedIds)
        val effectiveEnabled = config.enabled &&
            selection.size >= MIN_SLIDESHOW_SELECTION &&
            (config.intervalMinutes != null || config.changeOnAppToggle)
        val editor = prefs.edit()
        editor.putBoolean(KEY_SLIDESHOW_ENABLED, effectiveEnabled)
        if (config.intervalMinutes != null && config.intervalMinutes > 0) {
            editor.putInt(KEY_SLIDESHOW_INTERVAL_MINUTES, config.intervalMinutes)
        } else {
            editor.remove(KEY_SLIDESHOW_INTERVAL_MINUTES)
        }
        editor.putBoolean(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE, config.changeOnAppToggle)
        editor.putString(KEY_SLIDESHOW_SELECTION, selection.joinToString(","))
        if (effectiveEnabled) {
            editor.putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis())
        } else {
            editor.remove(KEY_SLIDESHOW_LAST_CHANGE)
        }
        editor.apply()
    }

    fun disableSlideshow(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SLIDESHOW_ENABLED, false)
            .remove(KEY_SLIDESHOW_INTERVAL_MINUTES)
            .remove(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE)
            .remove(KEY_SLIDESHOW_SELECTION)
            .remove(KEY_SLIDESHOW_LAST_CHANGE)
            .apply()
    }

    fun maybeAdvanceByTime(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val config = getSlideshowConfig(context)
        val intervalMinutes = config.intervalMinutes ?: return false
        if (!config.enabled || intervalMinutes <= 0) return false
        val lastChange = prefs(context).getLong(KEY_SLIDESHOW_LAST_CHANGE, 0L)
        val intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        if (now - lastChange >= intervalMs) {
            return advanceWallpaper(context) != null
        }
        return false
    }

    fun advanceOnAppToggle(context: Context): Boolean {
        val config = getSlideshowConfig(context)
        if (!config.enabled || !config.changeOnAppToggle) return false
        return advanceWallpaper(context) != null
    }

    fun advanceWallpaper(context: Context): String? {
        val config = getSlideshowConfig(context)
        if (!config.enabled) return null
        val pool = rotationPool(context, config)
        if (pool.size < MIN_SLIDESHOW_SELECTION) return null

        val currentId = getActiveId(context)
        val currentIndex = pool.indexOf(currentId)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % pool.size
        val nextId = pool[nextIndex]

        setActive(context, nextId)
        prefs(context).edit().putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis()).apply()
        return nextId
    }

    fun selectedCount(context: Context): Int {
        return sanitizeSelection(context, getSlideshowConfig(context).selectedIds).size
    }

    private fun rotationPool(context: Context, config: SlideshowConfig): List<String> {
        val selection = sanitizeSelection(context, config.selectedIds)
        return if (selection.size >= MIN_SLIDESHOW_SELECTION) selection else emptyList()
    }

    private fun sanitizeSelection(context: Context, selection: List<String>): List<String> {
        val available = getWallpaperList(context).toSet()
        return selection.filter { available.contains(it) }.take(MAX_SLIDESHOW_SELECTION)
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        var sampleSize = 1
        while ((bounds.outWidth / (sampleSize * 2)) >= reqWidth && (bounds.outHeight / (sampleSize * 2)) >= reqHeight) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = max(1, sampleSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }
}
