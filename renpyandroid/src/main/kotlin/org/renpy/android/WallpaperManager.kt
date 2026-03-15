package org.renpy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * Manages user-wallpapers stored in external storage.
 */
object WallpaperManager {

    private const val PREFS_KEY = "active_wallpaper"
    private const val DEFAULT_ID = "default"
    private const val WALLPAPERS_DIR = "wallpapers"

    private fun getWallpapersDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), WALLPAPERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getActiveId(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREFS_KEY, DEFAULT_ID) ?: DEFAULT_ID
    }

    fun setActive(context: Context, id: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY, id).apply()
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
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
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
}
