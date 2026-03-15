package org.renpy.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import org.renpy.android.databinding.ActivityWallpapersBinding

class WallpapersActivity : GameWindowActivity() {

    private lateinit var binding: ActivityWallpapersBinding
    private lateinit var adapter: WallpapersAdapter

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val intent = Intent(this, WallpaperCropActivity::class.java)
            intent.putExtra("image_uri", uri.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWallpapersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.launcher_wallpapers)

        setupGrid()
    }

    override fun onResume() {
        super.onResume()
        refreshGrid()
    }

    private fun setupGrid() {
        val items = WallpaperManager.getWallpaperList(this)
        val activeId = WallpaperManager.getActiveId(this)

        adapter = WallpapersAdapter(
            items = items,
            activeId = activeId,
            onItemClick = { id -> selectWallpaper(id) },
            onItemLongClick = { id -> confirmDelete(id) },
            onAddClick = { pickImage() }
        )

        binding.wallpapersRecycler.layoutManager = GridLayoutManager(this, 3)
        binding.wallpapersRecycler.adapter = adapter
    }

    private fun refreshGrid() {
        val items = WallpaperManager.getWallpaperList(this)
        val activeId = WallpaperManager.getActiveId(this)
        adapter.updateItems(items, activeId)
    }

    private fun selectWallpaper(id: String) {
        WallpaperManager.setActive(this, id)
        adapter.updateActive(id)
        Toast.makeText(this, getString(R.string.wallpaper_applied), Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(id: String) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.wallpaper_delete_title))
            .setMessage(getString(R.string.wallpaper_delete_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                WallpaperManager.deleteWallpaper(this, id)
                refreshGrid()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickImage() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

