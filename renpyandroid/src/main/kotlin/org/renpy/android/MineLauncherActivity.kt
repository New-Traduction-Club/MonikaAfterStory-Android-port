package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.renpy.android.databinding.ActivityMineLauncherBinding
import org.renpy.android.databinding.DialogMineGameSettingsBinding
import org.renpy.android.databinding.ItemMineGameCardBinding
import org.renpy.android.databinding.ItemMineGameListBinding
import java.io.File
import java.io.FileOutputStream

class MineLauncherActivity : GameWindowActivity() {

    companion object {
        const val PREFS_NAME = "mine_launcher_prefs"
        const val KEY_VIEW_MODE = "view_mode"
        const val VIEW_MODE_GRID = "grid"
        const val VIEW_MODE_LIST = "list"

        private val RUNTIME_OPTIONS = arrayOf(
            "Ren'Py 6.99",
            "Ren'Py 7.4.11",
            "Ren'Py 7.8.4",
            "Ren'Py 8.0.3",
            "Ren'Py 8.3.7",
            "Ren'Py 8.4.1",
            "Ren'Py 8.5.3"
        )
    }

    private lateinit var binding: ActivityMineLauncherBinding
    private lateinit var gamesAdapter: MineGamesAdapter

    private var allGames: List<File> = emptyList()
    private var isGridView: Boolean = true
    private var selectedGame: File? = null

    private val coverArtCache = LruCache<String, Bitmap>(16)
    private val ambientBlurCache = LruCache<String, Bitmap>(16)

    private var pendingCoverArtGameFolder: File? = null
    private var tempCoverSourceFile: File? = null
    private var tempCoverOutputFile: File? = null
    private var activeSettingsDialogBinding: DialogMineGameSettingsBinding? = null

    private val cropCoverArtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val folder = pendingCoverArtGameFolder
        val output = tempCoverOutputFile
        if (result.resultCode == Activity.RESULT_OK && folder != null && output != null && output.exists()) {
            MineLauncherConfigHelper.saveCoverArt(folder, output)
            invalidateCoverArt(folder)
            refreshGameViews(folder)
            activeSettingsDialogBinding?.let { db ->
                val hasCover = MineLauncherConfigHelper.hasCoverArt(folder)
                db.tvCoverArtActionTitle.text = getString(
                    if (hasCover) R.string.mine_launcher_change_cover_art
                    else R.string.mine_launcher_add_cover_art
                )
                db.rowRemoveCoverArt.visibility = if (hasCover) View.VISIBLE else View.GONE
            }
            InAppNotifier.show(this, getString(R.string.mine_launcher_cover_art_updated))
        }
        tempCoverSourceFile?.delete()
        tempCoverOutputFile?.delete()
        tempCoverSourceFile = null
        tempCoverOutputFile = null
    }

    private val pickCoverArtLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val folder = pendingCoverArtGameFolder ?: return@registerForActivityResult
        try {
            val sourceFile = File(cacheDir, "cover_src_${System.currentTimeMillis()}.png").also {
                tempCoverSourceFile = it
            }
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(sourceFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@registerForActivityResult

            val outputFile = File(cacheDir, "cover_out_${System.currentTimeMillis()}.png").also {
                tempCoverOutputFile = it
            }

            launchCoverArtCrop(Uri.fromFile(sourceFile), Uri.fromFile(outputFile))
        } catch (_: Exception) {
        }
    }

    private fun launchCoverArtCrop(sourceUri: Uri, outputUri: Uri) {
        val cropOptions = CropImageOptions(
            imageSourceIncludeGallery = false,
            imageSourceIncludeCamera = false,
            guidelines = CropImageView.Guidelines.ON,
            fixAspectRatio = true,
            aspectRatioX = 16,
            aspectRatioY = 9,
            autoZoomEnabled = true,
            multiTouchEnabled = true,
            centerMoveEnabled = true,
            canChangeCropWindow = true,
            initialCropWindowPaddingRatio = 0f,
            maxZoom = 8,
            outputCompressFormat = Bitmap.CompressFormat.PNG,
            outputCompressQuality = 100,
            outputRequestWidth = 960,
            outputRequestHeight = 540,
            outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_EXACT,
            customOutputUri = outputUri,
            activityTitle = getString(R.string.mine_launcher_cover_art_crop_title),
            cropMenuCropButtonTitle = getString(R.string.mine_launcher_save),
            activityBackgroundColor = ContextCompat.getColor(this, R.color.colorWindowContentBackground),
            toolbarColor = ContextCompat.getColor(this, R.color.colorWindowHeaderBackground),
            toolbarTitleColor = ContextCompat.getColor(this, R.color.colorTextPrimary),
            toolbarBackButtonColor = ContextCompat.getColor(this, R.color.colorPrimary),
            toolbarTintColor = ContextCompat.getColor(this, R.color.colorPrimary),
            activityMenuTextColor = ContextCompat.getColor(this, R.color.colorPrimary),
            activityMenuIconColor = ContextCompat.getColor(this, R.color.colorPrimary),
            backgroundColor = 0x88000000.toInt(),
            borderLineColor = ContextCompat.getColor(this, R.color.colorPrimary),
            borderCornerColor = ContextCompat.getColor(this, R.color.colorPrimary),
            guidelinesColor = ContextCompat.getColor(this, R.color.colorDivider)
        )

        val cropIntent = Intent(this, FullscreenCropImageActivity::class.java).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(
                CropImage.CROP_IMAGE_EXTRA_BUNDLE,
                Bundle(2).apply {
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, sourceUri)
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, cropOptions)
                }
            )
        }
        cropCoverArtLauncher.launch(cropIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMineLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.title_mine)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isGridView = prefs.getString(KEY_VIEW_MODE, VIEW_MODE_GRID) != VIEW_MODE_LIST

        setupRecyclerView()
        setupTopBar()
        setupDetailsView()
        loadGames()
    }

    override fun onDestroy() {
        super.onDestroy()
        tempCoverSourceFile?.delete()
        tempCoverOutputFile?.delete()
    }

    private fun setupRecyclerView() {
        gamesAdapter = MineGamesAdapter(
            isGridView = isGridView,
            getEngine = { file -> getGameEngine(file) },
            getTitle = { file -> getGameTitle(file) },
            getCoverArt = { file -> getCoverArtBitmap(file) },
            onCardClick = { file -> showDetailsView(file) },
            onPlayClick = { file -> launchSelectedGame(file) },
            onSettingsClick = { file -> showGameSettingsDialog(file) }
        )

        updateLayoutManager()
        binding.rvGames.adapter = gamesAdapter
    }

    private fun updateLayoutManager() {
        if (isGridView) {
            val cols = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
            binding.rvGames.layoutManager = GridLayoutManager(this, cols)
            binding.btnToggleView.setImageResource(R.drawable.ic_view_list)
            binding.btnToggleView.contentDescription = getString(R.string.mine_launcher_view_list)
        } else {
            binding.rvGames.layoutManager = LinearLayoutManager(this)
            binding.btnToggleView.setImageResource(R.drawable.ic_view_grid)
            binding.btnToggleView.contentDescription = getString(R.string.mine_launcher_view_grid)
        }
    }

    private val addGameLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadGames()
        }
    }

    override fun onResume() {
        super.onResume()
        loadGames()
    }

    private fun setupTopBar() {
        binding.btnAddGame.setOnClickListener {
            SoundEffects.playClick(this)
            addGameLauncher.launch(Intent(this, AddGameActivity::class.java))
        }

        binding.btnToggleView.setOnClickListener {
            SoundEffects.playClick(this)
            isGridView = !isGridView
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VIEW_MODE, if (isGridView) VIEW_MODE_GRID else VIEW_MODE_LIST)
                .apply()

            gamesAdapter.setGridView(isGridView)
            updateLayoutManager()
        }

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            binding.btnSearchClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            filterGames(query)
        }

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun setupDetailsView() {
        val onBackClick = View.OnClickListener {
            SoundEffects.playClick(this)
            showLibraryView()
        }
        binding.btnBackToLibrary.setOnClickListener(onBackClick)
        binding.btnDefaultBackToLibrary.setOnClickListener(onBackClick)
    }

    private fun loadGames() {
        lifecycleScope.launch {
            val games = withContext(Dispatchers.IO) {
                scanForGames()
            }
            allGames = games
            filterGames(binding.etSearch.text?.toString().orEmpty())
        }
    }

    private fun filterGames(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allGames
        } else {
            allGames.filter { game ->
                game.name.lowercase().contains(q) ||
                        getGameTitle(game).lowercase().contains(q)
            }
        }

        gamesAdapter.submitList(filtered)
        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetailsView(game: File) {
        SoundEffects.playClick(this)
        selectedGame = game
        updateDetailsViewContent(game)

        val onPlayClick = View.OnClickListener {
            SoundEffects.playClick(this)
            launchSelectedGame(game)
        }
        val onSettingsClick = View.OnClickListener {
            SoundEffects.playClick(this)
            showGameSettingsDialog(game)
        }
        val onTitleClick = View.OnClickListener {
            SoundEffects.playClick(this)
            showEditTitleDialog(game)
        }
        val onRuntimeClick = View.OnClickListener {
            SoundEffects.playClick(this)
            showRuntimeDialog(game) {
                refreshGameViews(game)
            }
        }

        binding.btnDetailsPlay.setOnClickListener(onPlayClick)
        binding.btnDetailsSettings.setOnClickListener(onSettingsClick)
        binding.rowTitleOption.setOnClickListener(onTitleClick)
        binding.rowRuntimeOption.setOnClickListener(onRuntimeClick)

        binding.btnDefaultPlay.setOnClickListener(onPlayClick)
        binding.btnDefaultSettings.setOnClickListener(onSettingsClick)
        binding.rowDefaultTitleOption.setOnClickListener(onTitleClick)
        binding.rowDefaultRuntimeOption.setOnClickListener(onRuntimeClick)

        binding.libraryContainer.visibility = View.GONE
        binding.detailsContainer.visibility = View.VISIBLE
    }

    private fun updateDetailsViewContent(game: File) {
        val title = getGameTitle(game)
        val engine = getGameEngine(game)
        val runtimeText = if (engine != null) {
            getString(R.string.experiments_runtime_badge, engine)
        } else {
            getString(R.string.experiments_runtime_not_selected)
        }
        val pathText = "filesDir/${game.name}/"

        if (MineLauncherConfigHelper.hasCoverArt(game)) {
            val coverBmp = getCoverArtBitmap(game)
            if (coverBmp != null) {
                binding.layoutCoverArtDetails.visibility = View.VISIBLE
                binding.layoutDefaultDetails.visibility = View.GONE
                binding.hsvDetailsCards.post {
                    val containerWidth = binding.hsvDetailsCards.width
                    val contentWidth = binding.hsvDetailsCards.getChildAt(0)?.width ?: 0
                    if (contentWidth > containerWidth) {
                        val targetScrollX = (contentWidth - containerWidth) / 2
                        binding.hsvDetailsCards.scrollTo(targetScrollX, 0)
                    } else {
                        binding.hsvDetailsCards.scrollTo(0, 0)
                    }
                }

                val ambientBmp = getAmbientBlurBitmap(game, coverBmp)
                binding.ivHeroAmbientBlur.setImageBitmap(ambientBmp)
                binding.ivHeroCoverArt.setImageBitmap(coverBmp)

                binding.tvHeroDetailsTitle.text = title
                binding.tvHeroDetailsRuntimeBadge.text = runtimeText
                binding.tvDetailsSelectedTitle.text = title
                binding.tvDetailsSelectedRuntime.text = runtimeText
                binding.tvDetailsPath.text = pathText
                return
            }
        }

        binding.layoutCoverArtDetails.visibility = View.GONE
        binding.layoutDefaultDetails.visibility = View.VISIBLE
        binding.ivHeroCoverArt.setImageDrawable(null)
        binding.ivHeroAmbientBlur.setImageDrawable(null)

        binding.tvHeroRotatedWatermark.text = title
        binding.tvDetailsTitle.text = title
        binding.tvDetailsRuntimeBadge.text = runtimeText
        binding.tvDefaultSelectedTitle.text = title
        binding.tvDefaultSelectedRuntime.text = runtimeText
        binding.tvDefaultDetailsPath.text = pathText
    }

    private fun showLibraryView() {
        selectedGame = null
        binding.detailsContainer.visibility = View.GONE
        binding.libraryContainer.visibility = View.VISIBLE
    }

    private fun refreshGameViews(gameFolder: File) {
        allGames = MineLauncherConfigHelper.sortGames(allGames)
        gamesAdapter.notifyDataSetChanged()
        filterGames(binding.etSearch.text?.toString().orEmpty())
        selectedGame?.let {
            if (it.absolutePath == gameFolder.absolutePath) {
                updateDetailsViewContent(gameFolder)
            }
        }
    }

    private fun launchSelectedGame(gameFolder: File) {
        val engine = getGameEngine(gameFolder)
        if (engine != null) {
            launchGame(gameFolder, engine)
        } else {
            showRuntimeDialog(gameFolder) { chosenEngine ->
                refreshGameViews(gameFolder)
                launchGame(gameFolder, chosenEngine)
            }
        }
    }

    private fun launchGame(gameFolder: File, engine: String) {
        SoundEffects.playClick(this)
        ExperimentsActivity.ensureDeandroidPatch(gameFolder)

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (engine) {
                    ExperimentsActivity.RUNTIME_853 -> Renpy853Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    ExperimentsActivity.RUNTIME_841 -> Renpy841Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    ExperimentsActivity.RUNTIME_837 -> Renpy837Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    ExperimentsActivity.RUNTIME_803 -> Renpy803Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    ExperimentsActivity.RUNTIME_7411 -> Renpy7411Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    ExperimentsActivity.RUNTIME_784 -> Renpy784Installer.ensureInstalled(
                        this@MineLauncherActivity,
                        gameFolder
                    )

                    else -> Renpy699Installer.ensureInstalled(this@MineLauncherActivity, gameFolder)
                }
            }

            if (ok) {
                val targetIntent = when (engine) {
                    ExperimentsActivity.RUNTIME_853 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity853::class.java
                    )

                    ExperimentsActivity.RUNTIME_841 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity841::class.java
                    )

                    ExperimentsActivity.RUNTIME_837 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity837::class.java
                    )

                    ExperimentsActivity.RUNTIME_803 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity803::class.java
                    )

                    ExperimentsActivity.RUNTIME_7411 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity7411::class.java
                    )

                    ExperimentsActivity.RUNTIME_784 -> Intent(
                        this@MineLauncherActivity,
                        PythonSDLActivity784::class.java
                    )

                    else -> Intent(this@MineLauncherActivity, getFreeActivityClass())
                }.apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("base_dir", gameFolder.name)
                }
                startActivity(targetIntent)
            } else {
                Toast.makeText(
                    this@MineLauncherActivity,
                    getString(R.string.experiments_failed_prepare_runtime, engine),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private enum class SettingsScreen {
        MENU,
        EDIT_TITLE,
        SELECT_RUNTIME
    }

    private fun showGameSettingsDialog(
        gameFolder: File,
        initialScreen: SettingsScreen = SettingsScreen.MENU,
        onRuntimeSelected: ((String) -> Unit)? = null,
        onDismissed: (() -> Unit)? = null
    ) {
        val dialogBinding = DialogMineGameSettingsBinding.inflate(layoutInflater)
        val currentTitle = getGameTitle(gameFolder)
        val currentEngine = getGameEngine(gameFolder)

        dialogBinding.tvCurrentTitleValue.text = currentTitle
        dialogBinding.tvCurrentRuntimeValue.text = if (currentEngine != null) {
            getString(R.string.experiments_runtime_badge, currentEngine)
        } else {
            getString(R.string.experiments_runtime_not_selected)
        }

        val config = getGameConfig(gameFolder)
        val defaultName = getGameDefaultDisplayName(gameFolder)
        dialogBinding.etGameTitle.setText(config.title ?: "")
        dialogBinding.etGameTitle.hint = defaultName
        dialogBinding.btnResetDefaultTitle.setOnClickListener {
            SoundEffects.playClick(this)
            dialogBinding.etGameTitle.setText("")
        }

        val (detectedVersion, recommendedVersion) = RenpyVersionDetector.detectAndSave(gameFolder)
        val recommendedIndex = when (recommendedVersion) {
            ExperimentsActivity.RUNTIME_853 -> 6
            ExperimentsActivity.RUNTIME_841 -> 5
            ExperimentsActivity.RUNTIME_837 -> 4
            ExperimentsActivity.RUNTIME_803 -> 3
            ExperimentsActivity.RUNTIME_784 -> 2
            ExperimentsActivity.RUNTIME_7411 -> 1
            ExperimentsActivity.RUNTIME_699 -> 0
            else -> 0
        }

        var selectedRuntimeIndex = if (currentEngine != null) {
            when (currentEngine) {
                ExperimentsActivity.RUNTIME_853 -> 6
                ExperimentsActivity.RUNTIME_841 -> 5
                ExperimentsActivity.RUNTIME_837 -> 4
                ExperimentsActivity.RUNTIME_803 -> 3
                ExperimentsActivity.RUNTIME_784 -> 2
                ExperimentsActivity.RUNTIME_7411 -> 1
                else -> 0
            }
        } else {
            recommendedIndex
        }

        val detectedText = if (detectedVersion != null) {
            getString(R.string.runtime_detection_found, detectedVersion)
        } else {
            getString(R.string.runtime_detection_found, getString(R.string.runtime_detection_unknown))
        }
        val recommendedText = if (recommendedVersion != null) {
            getString(R.string.runtime_detection_recommended, recommendedVersion)
        } else {
            getString(R.string.runtime_detection_recommended, getString(R.string.runtime_detection_none))
        }
        dialogBinding.tvRuntimeDetectedVersion.text = detectedText
        dialogBinding.tvRuntimeRecommendedVersion.text = recommendedText

        val runtimeAdapter = object :
            ArrayAdapter<CharSequence>(this, android.R.layout.simple_list_item_single_choice, RUNTIME_OPTIONS) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(this@MineLauncherActivity, R.color.colorTextPrimary))
                textView.textSize = 14f
                view.setBackgroundColor(
                    ContextCompat.getColor(
                        this@MineLauncherActivity,
                        R.color.colorWindowContentBackground
                    )
                )
                if (view is android.widget.CheckedTextView) {
                    val tintColor = ContextCompat.getColor(this@MineLauncherActivity, R.color.colorPrimary)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        view.checkMarkTintList = android.content.res.ColorStateList.valueOf(tintColor)
                    }
                }
                return view
            }
        }
        dialogBinding.listViewRuntime.adapter = runtimeAdapter
        dialogBinding.listViewRuntime.setItemChecked(selectedRuntimeIndex, true)
        dialogBinding.listViewRuntime.setOnItemClickListener { _, _, position, _ ->
            SoundEffects.playClick(this)
            selectedRuntimeIndex = position
        }

        var currentScreen = initialScreen
        var dialog: AlertDialog? = null

        val allScreens = listOf(
            dialogBinding.layoutSettingsMenu,
            dialogBinding.layoutEditTitle,
            dialogBinding.layoutSelectRuntime
        )

        fun transitionTo(nextScreen: SettingsScreen, animate: Boolean = true) {
            val titleView = dialog?.findViewById<TextView>(R.id.dialogTitle)
            val positiveButton = dialog?.findViewById<TextView>(R.id.dialogPositiveButton)
            val negativeButton = dialog?.findViewById<TextView>(R.id.dialogNegativeButton)

            val fromView = when (currentScreen) {
                SettingsScreen.MENU -> dialogBinding.layoutSettingsMenu
                SettingsScreen.EDIT_TITLE -> dialogBinding.layoutEditTitle
                SettingsScreen.SELECT_RUNTIME -> dialogBinding.layoutSelectRuntime
            }
            val toView = when (nextScreen) {
                SettingsScreen.MENU -> dialogBinding.layoutSettingsMenu
                SettingsScreen.EDIT_TITLE -> dialogBinding.layoutEditTitle
                SettingsScreen.SELECT_RUNTIME -> dialogBinding.layoutSelectRuntime
            }
            currentScreen = nextScreen

            for (screen in allScreens) {
                if (screen != fromView && screen != toView) {
                    screen.visibility = View.GONE
                    screen.alpha = 0f
                }
            }

            if (animate && fromView != toView) {
                toView.alpha = 0f
                toView.visibility = View.VISIBLE
                toView.animate().alpha(1f).setDuration(150).start()
                fromView.animate().alpha(0f).setDuration(150).withEndAction {
                    fromView.visibility = View.GONE
                }.start()
            } else {
                for (screen in allScreens) {
                    if (screen == toView) {
                        screen.visibility = View.VISIBLE
                        screen.alpha = 1f
                    } else {
                        screen.visibility = View.GONE
                        screen.alpha = 0f
                    }
                }
            }

            when (nextScreen) {
                SettingsScreen.MENU -> {
                    titleView?.text = getString(R.string.mine_launcher_options_title)
                    positiveButton?.visibility = View.GONE
                    negativeButton?.text = getString(R.string.cancel)
                    negativeButton?.visibility = View.VISIBLE
                    negativeButton?.setOnClickListener {
                        SoundEffects.playClick(this)
                        dialog?.dismiss()
                    }
                }

                SettingsScreen.EDIT_TITLE -> {
                    titleView?.text = getString(R.string.mine_launcher_edit_title)
                    negativeButton?.text = getString(R.string.mine_launcher_back)
                    negativeButton?.visibility = View.VISIBLE
                    negativeButton?.setOnClickListener {
                        SoundEffects.playClick(this)
                        if (initialScreen == SettingsScreen.EDIT_TITLE) {
                            dialog?.dismiss()
                        } else {
                            transitionTo(SettingsScreen.MENU)
                        }
                    }
                    positiveButton?.text = getString(R.string.mine_launcher_save)
                    positiveButton?.visibility = View.VISIBLE
                    positiveButton?.setOnClickListener {
                        SoundEffects.playClick(this)
                        val newTitle = dialogBinding.etGameTitle.text?.toString()?.trim()
                        setGameTitle(gameFolder, newTitle)
                        dialogBinding.tvCurrentTitleValue.text = getGameTitle(gameFolder)
                        refreshGameViews(gameFolder)
                        if (initialScreen == SettingsScreen.EDIT_TITLE) {
                            dialog?.dismiss()
                        } else {
                            transitionTo(SettingsScreen.MENU)
                        }
                    }
                }

                SettingsScreen.SELECT_RUNTIME -> {
                    titleView?.text = getString(R.string.experiments_select_runtime)
                    negativeButton?.text = getString(R.string.mine_launcher_back)
                    negativeButton?.visibility = View.VISIBLE
                    negativeButton?.setOnClickListener {
                        SoundEffects.playClick(this)
                        if (initialScreen == SettingsScreen.SELECT_RUNTIME) {
                            dialog?.dismiss()
                        } else {
                            transitionTo(SettingsScreen.MENU)
                        }
                    }
                    positiveButton?.text = getString(R.string.experiments_select)
                    positiveButton?.visibility = View.VISIBLE
                    positiveButton?.setOnClickListener {
                        SoundEffects.playClick(this)
                        val chosenEngine = when (selectedRuntimeIndex) {
                            6 -> ExperimentsActivity.RUNTIME_853
                            5 -> ExperimentsActivity.RUNTIME_841
                            4 -> ExperimentsActivity.RUNTIME_837
                            3 -> ExperimentsActivity.RUNTIME_803
                            2 -> ExperimentsActivity.RUNTIME_784
                            1 -> ExperimentsActivity.RUNTIME_7411
                            else -> ExperimentsActivity.RUNTIME_699
                        }
                        setGameEngine(gameFolder, chosenEngine)
                        dialogBinding.tvCurrentRuntimeValue.text =
                            getString(R.string.experiments_runtime_badge, chosenEngine)
                        onRuntimeSelected?.invoke(chosenEngine)
                        refreshGameViews(gameFolder)
                        if (initialScreen == SettingsScreen.SELECT_RUNTIME) {
                            dialog?.dismiss()
                        } else {
                            transitionTo(SettingsScreen.MENU)
                        }
                    }
                }
            }
        }

        fun updateCoverArtRowState() {
            val hasCover = MineLauncherConfigHelper.hasCoverArt(gameFolder)
            dialogBinding.tvCoverArtActionTitle.text = getString(
                if (hasCover) R.string.mine_launcher_change_cover_art
                else R.string.mine_launcher_add_cover_art
            )
            dialogBinding.rowRemoveCoverArt.visibility = if (hasCover) View.VISIBLE else View.GONE
        }
        updateCoverArtRowState()

        dialogBinding.rowSettingTitle.setOnClickListener {
            SoundEffects.playClick(this)
            transitionTo(SettingsScreen.EDIT_TITLE)
        }

        dialogBinding.rowSettingCoverArt.setOnClickListener {
            SoundEffects.playClick(this)
            pendingCoverArtGameFolder = gameFolder
            activeSettingsDialogBinding = dialogBinding
            pickCoverArtLauncher.launch("image/*")
        }

        dialogBinding.rowRemoveCoverArt.setOnClickListener {
            SoundEffects.playClick(this)
            MineLauncherConfigHelper.deleteCoverArt(gameFolder)
            invalidateCoverArt(gameFolder)
            updateCoverArtRowState()
            refreshGameViews(gameFolder)
            InAppNotifier.show(this, getString(R.string.mine_launcher_cover_art_removed))
        }

        dialogBinding.rowSettingRuntime.setOnClickListener {
            SoundEffects.playClick(this)
            transitionTo(SettingsScreen.SELECT_RUNTIME)
        }

        val initialTitleRes = when (initialScreen) {
            SettingsScreen.MENU -> R.string.mine_launcher_options_title
            SettingsScreen.EDIT_TITLE -> R.string.mine_launcher_edit_title
            SettingsScreen.SELECT_RUNTIME -> R.string.experiments_select_runtime
        }

        dialog = GameDialogBuilder(this)
            .setTitle(initialTitleRes)
            .setView(dialogBinding.root)
            .setHeightPercentage(0.80f)
            .setFadeAnimation(true)
            .setPositiveButton(getString(R.string.mine_launcher_save)) { _, _ -> }
            .setNegativeButton(getString(R.string.cancel)) { d, _ ->
                d.dismiss()
            }
            .setOnDismissListener {
                if (activeSettingsDialogBinding == dialogBinding) {
                    activeSettingsDialogBinding = null
                }
                onDismissed?.invoke()
            }
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (currentScreen != SettingsScreen.MENU && initialScreen == SettingsScreen.MENU) {
                    SoundEffects.playClick(this)
                    transitionTo(SettingsScreen.MENU)
                    return@setOnKeyListener true
                }
            }
            false
        }

        dialog.show()

        transitionTo(initialScreen, animate = false)
    }

    private fun showEditTitleDialog(gameFolder: File, onDismissed: (() -> Unit)? = null) {
        showGameSettingsDialog(
            gameFolder = gameFolder,
            initialScreen = SettingsScreen.EDIT_TITLE,
            onDismissed = onDismissed
        )
    }

    private fun showRuntimeDialog(
        gameFolder: File,
        onSelected: ((String) -> Unit)? = null
    ) {
        showRuntimeDialog(gameFolder, onSelected, onDismissed = null)
    }

    private fun showRuntimeDialog(
        gameFolder: File,
        onSelected: ((String) -> Unit)?,
        onDismissed: (() -> Unit)?
    ) {
        showGameSettingsDialog(
            gameFolder = gameFolder,
            initialScreen = SettingsScreen.SELECT_RUNTIME,
            onRuntimeSelected = onSelected,
            onDismissed = onDismissed
        )
    }

    fun getGameConfig(gameFolder: File): MineLauncherConfigHelper.MineGameConfig {
        return MineLauncherConfigHelper.getGameConfig(gameFolder)
    }

    fun saveGameConfig(gameFolder: File, config: MineLauncherConfigHelper.MineGameConfig) {
        MineLauncherConfigHelper.saveGameConfig(gameFolder, config)
    }

    fun getGameTitle(gameFolder: File): String {
        return MineLauncherConfigHelper.getGameTitle(gameFolder)
    }

    fun getGameEngine(gameFolder: File): String? {
        return MineLauncherConfigHelper.getGameEngine(gameFolder)
    }

    fun setGameEngine(gameFolder: File, engine: String) {
        MineLauncherConfigHelper.setGameEngine(gameFolder, engine)
    }

    fun setGameTitle(gameFolder: File, title: String?) {
        MineLauncherConfigHelper.setGameTitle(gameFolder, title)
    }

    fun getGameDefaultDisplayName(file: File): String {
        return MineLauncherConfigHelper.getGameDefaultDisplayName(file)
    }

    private fun scanForGames(): List<File> {
        val root = filesDir ?: return emptyList()
        val list = mutableListOf<File>()
        val children = root.listFiles() ?: return emptyList()

        for (child in children) {
            if (child.isDirectory && child.name != ExperimentsActivity.EXCLUDED_MAS_DIR) {
                val gameSubDir = File(child, "game")
                if (gameSubDir.exists() && gameSubDir.isDirectory) {
                    list.add(child)
                    RenpyVersionDetector.detectAndSave(child)
                }
            }
        }
        return MineLauncherConfigHelper.sortGames(list)
    }

    private fun getFreeActivityClass(): Class<out PythonSDLActivity> {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return PythonSDLActivity2::class.java
        val runningProcesses = manager.runningAppProcesses ?: return PythonSDLActivity2::class.java

        var isRenpy2Running = false
        var isRenpy3Running = false

        val prefix = packageName
        for (processInfo in runningProcesses) {
            if (processInfo.processName == "$prefix:renpy2") {
                isRenpy2Running = true
            }
            if (processInfo.processName == "$prefix:renpy3") {
                isRenpy3Running = true
            }
        }

        return if (!isRenpy2Running) {
            PythonSDLActivity2::class.java
        } else if (!isRenpy3Running) {
            PythonSDLActivity3::class.java
        } else {
            PythonSDLActivity2::class.java
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.detailsContainer.visibility == View.VISIBLE) {
            showLibraryView()
        } else {
            super.onBackPressed()
        }
    }

    fun getCoverArtBitmap(gameFolder: File): Bitmap? {
        val path = gameFolder.absolutePath
        coverArtCache.get(path)?.let { if (!it.isRecycled) return it }
        val file = MineLauncherConfigHelper.getCoverArtFile(gameFolder)
        if (!file.exists()) return null
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                coverArtCache.put(path, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun getAmbientBlurBitmap(gameFolder: File, sourceBitmap: Bitmap?): Bitmap? {
        val path = gameFolder.absolutePath
        ambientBlurCache.get(path)?.let { if (!it.isRecycled) return it }
        val src = sourceBitmap ?: getCoverArtBitmap(gameFolder) ?: return null
        return try {
            val smallWidth = 120
            val smallHeight = 68
            val downscaled = Bitmap.createScaledBitmap(src, smallWidth, smallHeight, true)
            val blurred = FastBlur.stackBlur(downscaled, 12)
            ambientBlurCache.put(path, blurred)
            blurred
        } catch (_: Exception) {
            null
        }
    }

    fun invalidateCoverArt(gameFolder: File) {
        val path = gameFolder.absolutePath
        coverArtCache.remove(path)
        ambientBlurCache.remove(path)
    }

    private class MineGamesAdapter(
        private var isGridView: Boolean,
        private val getEngine: (File) -> String?,
        private val getTitle: (File) -> String,
        private val getCoverArt: (File) -> Bitmap?,
        private val onCardClick: (File) -> Unit,
        private val onPlayClick: (File) -> Unit,
        private val onSettingsClick: (File) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<File> = emptyList()

        companion object {
            private const val TYPE_CARD = 1
            private const val TYPE_LIST = 2
        }

        fun submitList(newItems: List<File>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setGridView(grid: Boolean) {
            if (isGridView != grid) {
                isGridView = grid
                notifyDataSetChanged()
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (isGridView) TYPE_CARD else TYPE_LIST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_CARD) {
                val binding = ItemMineGameCardBinding.inflate(inflater, parent, false)
                CardViewHolder(binding)
            } else {
                val binding = ItemMineGameListBinding.inflate(inflater, parent, false)
                ListViewHolder(binding)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val file = items[position]
            val title = getTitle(file)
            val context = holder.itemView.context
            val engine = getEngine(file)
            val runtimeText = if (engine != null) {
                context.getString(R.string.experiments_runtime_badge, engine)
            } else {
                context.getString(R.string.experiments_runtime_not_selected)
            }
            val coverBmp = getCoverArt(file)

            if (holder is CardViewHolder) {
                holder.binding.tvGameTitle.text = title
                holder.binding.tvRuntimeBadge.text = runtimeText

                if (coverBmp != null) {
                    holder.binding.ivCoverArt.visibility = View.VISIBLE
                    holder.binding.ivCoverArt.setImageBitmap(coverBmp)
                    holder.binding.tvRotatedFolderName.visibility = View.GONE
                } else {
                    holder.binding.ivCoverArt.visibility = View.GONE
                    holder.binding.ivCoverArt.setImageDrawable(null)
                    holder.binding.tvRotatedFolderName.visibility = View.VISIBLE
                    holder.binding.tvRotatedFolderName.text = title
                }

                holder.binding.cardContainer.setOnClickListener {
                    onCardClick(file)
                }
                holder.binding.btnQuickPlay.setOnClickListener {
                    onPlayClick(file)
                }
                holder.binding.btnQuickSettings.setOnClickListener {
                    onSettingsClick(file)
                }
            } else if (holder is ListViewHolder) {
                holder.binding.tvGameTitle.text = title
                holder.binding.tvGamePath.text = "filesDir/${file.name}/"
                holder.binding.tvRuntimeBadge.text = runtimeText

                if (coverBmp != null) {
                    holder.binding.cardListCover.visibility = View.VISIBLE
                    holder.binding.ivListCoverArt.setImageBitmap(coverBmp)
                } else {
                    holder.binding.cardListCover.visibility = View.GONE
                    holder.binding.ivListCoverArt.setImageDrawable(null)
                }

                holder.binding.cardContainer.setOnClickListener {
                    onCardClick(file)
                }
                holder.binding.btnQuickPlay.setOnClickListener {
                    onPlayClick(file)
                }
                holder.binding.btnQuickSettings.setOnClickListener {
                    onSettingsClick(file)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class CardViewHolder(val binding: ItemMineGameCardBinding) : RecyclerView.ViewHolder(binding.root)
        class ListViewHolder(val binding: ItemMineGameListBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
