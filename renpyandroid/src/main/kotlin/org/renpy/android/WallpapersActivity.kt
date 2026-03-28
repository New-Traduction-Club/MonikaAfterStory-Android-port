package org.renpy.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import org.renpy.android.databinding.ActivityWallpapersBinding

class WallpapersActivity : GameWindowActivity() {

    private lateinit var binding: ActivityWallpapersBinding
    private lateinit var adapter: WallpapersAdapter

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

        setupGrid()
        setupSlideshowControls()
        updateSlideshowStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshGrid()
        updateSlideshowStatus()
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

    private fun setupSlideshowControls() {
        binding.btnConfigureSlideshow.setOnClickListener {
            SoundEffects.playClick(this)
            showSlideshowDialog()
        }
        binding.btnDisableSlideshow.setOnClickListener {
            SoundEffects.playClick(this)
            WallpaperManager.disableSlideshow(this)
            updateSlideshowStatus()
            InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_disabled))
        }
    }

    private fun refreshGrid() {
        val items = WallpaperManager.getWallpaperList(this)
        val activeId = WallpaperManager.getActiveId(this)
        adapter.updateItems(items, activeId)
    }

    private fun selectWallpaper(id: String) {
        WallpaperManager.setActive(this, id)
        adapter.updateActive(id)
        applyActiveWallpaper()
        InAppNotifier.show(this, getString(R.string.wallpaper_applied))
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
            arrayOf("image/*", "video/*")
        )
    }

    private fun updateSlideshowStatus() {
        val config = WallpaperManager.getSlideshowConfig(this)
        if (!config.enabled) {
            binding.slideshowStatus.text = getString(R.string.wallpaper_slideshow_status_off)
            return
        }

        val parts = mutableListOf<String>()
        val selectionSize = config.selectedIds.size
        parts.add(getString(R.string.wallpaper_slideshow_selection_count, selectionSize))

        config.intervalMinutes?.let { minutes ->
            parts.add(formatInterval(minutes))
        }
        if (config.changeOnAppToggle) {
            parts.add(getString(R.string.wallpaper_slideshow_trigger_app_toggle))
        }

        val summary = parts.joinToString(" • ")
        binding.slideshowStatus.text = getString(R.string.wallpaper_slideshow_status_on, summary)
    }

    private fun showSlideshowDialog() {
        val config = WallpaperManager.getSlideshowConfig(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_slideshow_config, null)

        val timedCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxTimed)
        val intervalInput = dialogView.findViewById<EditText>(R.id.inputIntervalValue)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.spinnerIntervalUnit)
        val appToggleCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxAppToggle)
        val selectionHint = dialogView.findViewById<TextView>(R.id.selectionHint)
        val selectionSummary = dialogView.findViewById<TextView>(R.id.selectionSummary)
        val selectButton = dialogView.findViewById<Button>(R.id.btnSelectWallpapers)

        val wallpapers = WallpaperManager.getWallpaperList(this)
        if (wallpapers.size < 2) {
            InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_need_more), true)
            return
        }
        var selectedIds = if (config.selectedIds.isNotEmpty()) {
            config.selectedIds.toMutableList()
        } else {
            wallpapers.take(WallpaperManager.MAX_SLIDESHOW_SELECTION).toMutableList()
        }

        val unitOptions = listOf(
            getString(R.string.wallpaper_slideshow_unit_minutes),
            getString(R.string.wallpaper_slideshow_unit_hours)
        )
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, unitOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = spinnerAdapter

        if (config.intervalMinutes != null) {
            timedCheckbox.isChecked = true
            if (config.intervalMinutes % 60 == 0) {
                intervalInput.setText((config.intervalMinutes / 60).toString())
                unitSpinner.setSelection(1)
            } else {
                intervalInput.setText(config.intervalMinutes.toString())
                unitSpinner.setSelection(0)
            }
        }

        appToggleCheckbox.isChecked = config.changeOnAppToggle
        selectionHint.text = getString(R.string.wallpaper_slideshow_selection_hint)
        fun refreshSelectionSummary() {
            selectionSummary.text = getString(
                R.string.wallpaper_slideshow_selection_summary,
                selectedIds.size
            )
        }
        refreshSelectionSummary()

        val parentDialog = GameDialogBuilder(this)
            .setTitle(getString(R.string.wallpaper_slideshow_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.wallpaper_slideshow_save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .setCancelable(true)
            .create()

        selectButton.setOnClickListener {
            SoundEffects.playClick(this)
            parentDialog.hide()
            openWallpaperSelectionDialog(wallpapers, selectedIds, parentDialog) { updatedSelection ->
                selectedIds = updatedSelection.toMutableList()
                refreshSelectionSummary()
            }
        }

        parentDialog.show()

        val positiveButton = parentDialog.findViewById<TextView>(R.id.dialogPositiveButton)
        positiveButton?.setOnClickListener {
            SoundEffects.playClick(this)
            if (!isSelectionCountValid(selectedIds.size)) {
                InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_selection), true)
                return@setOnClickListener
            }

            val timedEnabled = timedCheckbox.isChecked
            val appToggleEnabled = appToggleCheckbox.isChecked
            if (!timedEnabled && !appToggleEnabled) {
                InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_mode), true)
                return@setOnClickListener
            }

            var intervalMinutes: Int? = null
            if (timedEnabled) {
                val valueText = intervalInput.text?.toString()?.trim().orEmpty()
                val valueNumber = valueText.toIntOrNull()
                if (valueNumber == null || valueNumber <= 0) {
                    InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_interval), true)
                    return@setOnClickListener
                }
                intervalMinutes = if (unitSpinner.selectedItemPosition == 1) valueNumber * 60 else valueNumber
            }

            val slideshowConfig = WallpaperManager.SlideshowConfig(
                enabled = true,
                intervalMinutes = intervalMinutes,
                changeOnAppToggle = appToggleEnabled,
                selectedIds = selectedIds
            )

            WallpaperManager.saveSlideshowConfig(this, slideshowConfig)
            updateSlideshowStatus()
            InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_applied))
            parentDialog.dismiss()
        }
    }

    private fun applyActiveWallpaper() {
        val root = window?.decorView?.rootView
        if (root != null) {
            WallpaperManager.applyWallpaper(this, root)
        }
    }

    private fun openWallpaperSelectionDialog(
        wallpapers: List<String>,
        currentSelection: List<String>,
        parentDialog: AlertDialog,
        onSelection: (List<String>) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_slideshow_selection_list, null)
        val listContainer = dialogView.findViewById<LinearLayout>(R.id.wallpaperSelectionContainer)
        val selectionHint = dialogView.findViewById<TextView>(R.id.selectionHint)
        selectionHint.text = getString(R.string.wallpaper_slideshow_selection_hint)
        val checkBoxes = mutableListOf<CheckBox>()

        fun checkedCount(): Int = checkBoxes.count { it.isChecked }

        fun refreshCheckboxConstraints() {
            val count = checkedCount()
            val atMin = count <= WallpaperManager.MIN_SLIDESHOW_SELECTION
            val atMax = count >= WallpaperManager.MAX_SLIDESHOW_SELECTION

            checkBoxes.forEach { checkBox ->
                val disableByMin = atMin && checkBox.isChecked
                val disableByMax = atMax && !checkBox.isChecked
                val disable = disableByMin || disableByMax
                checkBox.isEnabled = !disable
                checkBox.alpha = if (disable) 0.65f else 1f
            }
        }

        wallpapers.forEach { id ->
            val checkBox = CheckBox(this)
            checkBox.text = if (id == "default") getString(R.string.wallpaper_default) else id
            checkBox.tag = id
            checkBox.isChecked = currentSelection.contains(id)
            checkBox.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary))
            checkBox.setOnClickListener {
                val count = checkedCount()
                when {
                    count > WallpaperManager.MAX_SLIDESHOW_SELECTION -> {
                        checkBox.isChecked = false
                        InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_selection), true)
                    }
                    count < WallpaperManager.MIN_SLIDESHOW_SELECTION -> {
                        checkBox.isChecked = true
                        InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_selection), true)
                    }
                }
                refreshCheckboxConstraints()
            }

            checkBoxes.add(checkBox)
            listContainer.addView(checkBox)
        }
        refreshCheckboxConstraints()

        val selectionDialog = GameDialogBuilder(this)
            .setTitle(getString(R.string.wallpaper_slideshow_select_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.wallpaper_slideshow_select_apply), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                SoundEffects.playClick(this)
                parentDialog.show()
            }
            .setCancelable(true)
            .create()

        selectionDialog.setOnCancelListener {
            if (!parentDialog.isShowing) {
                parentDialog.show()
            }
        }
        selectionDialog.setOnDismissListener {
            if (!parentDialog.isShowing) {
                parentDialog.show()
            }
        }

        selectionDialog.show()

        val positiveButton = selectionDialog.findViewById<TextView>(R.id.dialogPositiveButton)
        positiveButton?.setOnClickListener {
            SoundEffects.playClick(this)
            val selected = checkBoxes
                .filter { it.isChecked }
                .map { it.tag as String }

            if (!isSelectionCountValid(selected.size)) {
                InAppNotifier.show(this, getString(R.string.wallpaper_slideshow_validation_selection), true)
                return@setOnClickListener
            }

            onSelection(selected)
            parentDialog.show()
            selectionDialog.dismiss()
        }
    }

    private fun formatInterval(minutes: Int): String {
        return if (minutes % 60 == 0) {
            val hours = minutes / 60
            getString(R.string.wallpaper_slideshow_interval_hours, hours)
        } else {
            getString(R.string.wallpaper_slideshow_interval_minutes, minutes)
        }
    }

    private fun isSelectionCountValid(count: Int): Boolean {
        return count in WallpaperManager.MIN_SLIDESHOW_SELECTION..WallpaperManager.MAX_SLIDESHOW_SELECTION
    }
}
