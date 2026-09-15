package org.renpy.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import org.renpy.android.databinding.ActivityWallpapersBinding

class WallpapersActivity : GameWindowActivity() {

    private lateinit var binding: ActivityWallpapersBinding
    private lateinit var adapter: WallpapersAdapter
    private var currentTarget: WallpaperManager.WallpaperTarget = WallpaperManager.WallpaperTarget.MAS

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
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

        currentTarget = WallpaperManager.getCurrentDesktopTarget(this)

        setupTabs()
        setupGrid()
    }

    override fun onResume() {
        super.onResume()
        refreshGrid()
    }

    private fun setupTabs() {
        updateTabSelectionUI()

        binding.tabMas.setOnClickListener {
            SoundEffects.playClick(this)
            switchTarget(WallpaperManager.WallpaperTarget.MAS)
        }

        binding.tabMine.setOnClickListener {
            SoundEffects.playClick(this)
            switchTarget(WallpaperManager.WallpaperTarget.MINE)
        }

        binding.tabLockscreen.setOnClickListener {
            SoundEffects.playClick(this)
            switchTarget(WallpaperManager.WallpaperTarget.LOCKSCREEN)
        }

        binding.btnApplyToAll.setOnClickListener {
            SoundEffects.playClick(this)
            val activeId = WallpaperManager.getActiveId(this, currentTarget)
            WallpaperManager.setAllActive(this, activeId)
            applyActiveWallpaper()
            InAppNotifier.show(this, getString(R.string.wallpaper_applied_all))
        }
    }

    private fun switchTarget(target: WallpaperManager.WallpaperTarget) {
        if (currentTarget == target) return
        currentTarget = target
        updateTabSelectionUI()
        val activeId = WallpaperManager.getActiveId(this, currentTarget)
        adapter.updateActive(activeId)
    }

    private fun updateTabSelectionUI() {
        binding.tabMas.isSelected = currentTarget == WallpaperManager.WallpaperTarget.MAS
        binding.tabMine.isSelected = currentTarget == WallpaperManager.WallpaperTarget.MINE
        binding.tabLockscreen.isSelected = currentTarget == WallpaperManager.WallpaperTarget.LOCKSCREEN
    }

    private fun setupGrid() {
        val items = WallpaperManager.getWallpaperList(this)
        val activeId = WallpaperManager.getActiveId(this, currentTarget)

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
        val activeId = WallpaperManager.getActiveId(this, currentTarget)
        adapter.updateItems(items, activeId)
    }

    private fun selectWallpaper(id: String) {
        WallpaperManager.setActive(this, currentTarget, id)
        adapter.updateActive(id)
        if (currentTarget == WallpaperManager.getCurrentDesktopTarget(this)) {
            applyActiveWallpaper()
        }
        val targetName = getTargetName(currentTarget)
        InAppNotifier.show(this, getString(R.string.wallpaper_applied_target, targetName))
    }

    private fun getTargetName(target: WallpaperManager.WallpaperTarget): String {
        return when (target) {
            WallpaperManager.WallpaperTarget.MAS -> getString(R.string.wallpaper_target_mas)
            WallpaperManager.WallpaperTarget.MINE -> getString(R.string.wallpaper_target_mine)
            WallpaperManager.WallpaperTarget.LOCKSCREEN -> getString(R.string.wallpaper_target_lockscreen)
        }
    }

    private fun confirmDelete(id: String) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.wallpaper_delete_title))
            .setMessage(getString(R.string.wallpaper_delete_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                WallpaperManager.deleteWallpaper(this, id)
                refreshGrid()
                if (currentTarget == WallpaperManager.getCurrentDesktopTarget(this)) {
                    applyActiveWallpaper()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickImage() {
        pickMediaLauncher.launch(
            arrayOf("image/*", "video/*")
        )
    }

    private fun applyActiveWallpaper() {
        val root = window?.decorView?.rootView
        if (root != null) {
            WallpaperManager.applyWallpaper(this, root)
        }
    }
}
