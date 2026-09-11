package org.renpy.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExperimentsActivity : GameWindowActivity() {

    private lateinit var rvExperiments: RecyclerView
    private lateinit var tvEmptyState: TextView

    companion object {
        const val RUNTIME_699 = "6.99"
        const val RUNTIME_784 = "7.8.4"
        const val RUNTIME_837 = "8.3.7"
        private val RUNTIME_OPTIONS = arrayOf("Renpy 6.99", "Renpy 7.8.4", "Renpy 8.3.7")
        const val EXCLUDED_MAS_DIR = "monikaafterstory-masl-edition"
        const val DEANDROID_RPY_CONTENT = "init -999 python:\n    renpy.android = False\n"

        @JvmStatic
        fun ensureDeandroidPatch(gameFolder: File): Boolean {
            if (gameFolder.name == EXCLUDED_MAS_DIR) {
                return false
            }
            val gameDir = if (gameFolder.name == "game" && gameFolder.isDirectory) {
                gameFolder
            } else {
                File(gameFolder, "game")
            }
            if (!gameDir.exists() || !gameDir.isDirectory) {
                return false
            }
            val patchesDir = File(gameDir, "a_masl_patches")
            val patchFile = File(patchesDir, "deandroid.rpy")
            if (!patchFile.exists()) {
                return try {
                    if (!patchesDir.exists()) {
                        patchesDir.mkdirs()
                    }
                    patchFile.writeText(DEANDROID_RPY_CONTENT)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experiments)

        setTitle(R.string.title_experiments)

        rvExperiments = findViewById(R.id.rvExperiments)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvExperiments.layoutManager = LinearLayoutManager(this)

        loadGames()
    }

    private fun loadGames() {
        lifecycleScope.launch {
            val games = withContext(Dispatchers.IO) {
                scanForGames()
            }

            if (games.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvExperiments.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvExperiments.visibility = View.VISIBLE
                rvExperiments.adapter = GamesAdapter(
                    games,
                    getEngine = { gameDir -> getGameEngine(gameDir) },
                    onLaunchClick = { gameFolder ->
                        val engine = getGameEngine(gameFolder)
                        if (engine != null) {
                            launchGame(gameFolder, engine)
                        } else {
                            showRuntimeSelectorDialog(gameFolder) { chosenEngine ->
                                launchGame(gameFolder, chosenEngine)
                            }
                        }
                    },
                    onChangeRuntimeClick = { gameFolder ->
                        showRuntimeSelectorDialog(gameFolder) {
                            rvExperiments.adapter?.notifyDataSetChanged()
                        }
                    }
                )
            }
        }
    }

    private fun getGameEngine(gameFolder: File): String? {
        val file = File(gameFolder, "engine.txt")
        if (!file.exists()) return null
        return try {
            val content = file.readText().trim()
            if (content == RUNTIME_699 || content == RUNTIME_784 || content == RUNTIME_837) content else null
        } catch (e: Exception) {
            null
        }
    }

    private fun setGameEngine(gameFolder: File, engine: String) {
        try {
            File(gameFolder, "engine.txt").writeText(engine)
            if (engine == RUNTIME_699) {
                File(gameFolder, ".runtime_784.version").delete()
                File(gameFolder, ".runtime_837.version").delete()
                File(gameFolder, ".private.version").delete()
            } else if (engine == RUNTIME_784) {
                File(gameFolder, ".runtime_837.version").delete()
                File(gameFolder, ".private.version").delete()
            } else if (engine == RUNTIME_837) {
                File(gameFolder, ".runtime_784.version").delete()
                File(gameFolder, ".private.version").delete()
            }
        } catch (e: Exception) {
            // ignore write errors
        }
    }

    private fun showRuntimeSelectorDialog(gameFolder: File, onSelected: ((String) -> Unit)? = null) {
        val currentEngine = getGameEngine(gameFolder)
        var selectedIndex = when (currentEngine) {
            RUNTIME_837 -> 2
            RUNTIME_784 -> 1
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Runtime")
            .setSingleChoiceItems(RUNTIME_OPTIONS, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Select") { _, _ ->
                val chosenEngine = when (selectedIndex) {
                    2 -> RUNTIME_837
                    1 -> RUNTIME_784
                    else -> RUNTIME_699
                }
                setGameEngine(gameFolder, chosenEngine)
                rvExperiments.adapter?.notifyDataSetChanged()
                onSelected?.invoke(chosenEngine)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchGame(gameFolder: File, engine: String) {
        SoundEffects.playClick(this@ExperimentsActivity)
        ensureDeandroidPatch(gameFolder)

        if (engine == RUNTIME_837) {
            lifecycleScope.launch {
                val progressDialog = MaterialAlertDialogBuilder(this@ExperimentsActivity)
                    .setMessage("Preparing Ren'Py 8.3.7 runtime...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                val ok = withContext(Dispatchers.IO) {
                    Renpy837Installer.ensureInstalled(this@ExperimentsActivity, gameFolder)
                }
                progressDialog.dismiss()

                if (ok) {
                    val intent = Intent(this@ExperimentsActivity, PythonSDLActivity837::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("base_dir", gameFolder.name)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@ExperimentsActivity,
                        "Failed to prepare Renpy 8.3.7 runtime",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else if (engine == RUNTIME_784) {
            lifecycleScope.launch {
                val progressDialog = MaterialAlertDialogBuilder(this@ExperimentsActivity)
                    .setMessage("Preparing Ren'Py 7.8.4 runtime...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                val ok = withContext(Dispatchers.IO) {
                    Renpy784Installer.ensureInstalled(this@ExperimentsActivity, gameFolder)
                }
                progressDialog.dismiss()

                if (ok) {
                    val intent = Intent(this@ExperimentsActivity, PythonSDLActivity784::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("base_dir", gameFolder.name)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@ExperimentsActivity,
                        "Failed to prepare Renpy 7.8.4 runtime",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            val targetClass = getFreeActivityClass()
            val intent = Intent(this@ExperimentsActivity, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("base_dir", gameFolder.name)
            }
            startActivity(intent)
        }
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

    private fun scanForGames(): List<File> {
        val root = filesDir ?: return emptyList()
        val list = mutableListOf<File>()
        val children = root.listFiles() ?: return emptyList()

        for (child in children) {
            if (child.isDirectory && child.name != EXCLUDED_MAS_DIR) {
                val gameSubDir = File(child, "game")
                if (gameSubDir.exists() && gameSubDir.isDirectory) {
                    list.add(child)
                }
            }
        }
        return list
    }

    private class GamesAdapter(
        private val items: List<File>,
        private val getEngine: (File) -> String?,
        private val onLaunchClick: (File) -> Unit,
        private val onChangeRuntimeClick: (File) -> Unit
    ) : RecyclerView.Adapter<GamesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvGameTitle: TextView = view.findViewById(R.id.tvGameTitle)
            val tvGamePath: TextView = view.findViewById(R.id.tvGamePath)
            val tvRuntimeBadge: TextView = view.findViewById(R.id.tvRuntimeBadge)
            val btnLaunchGame: Button = view.findViewById(R.id.btnLaunchGame)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_experiment_game, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = items[position]
            val displayName = file.name
                .replace("-", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            holder.tvGameTitle.text = displayName
            holder.tvGamePath.text = "filesDir/${file.name}/"

            val engine = getEngine(file)
            if (engine != null) {
                holder.tvRuntimeBadge.text = "Runtime: Renpy $engine"
            } else {
                holder.tvRuntimeBadge.text = "Runtime: Not selected"
            }

            holder.tvRuntimeBadge.setOnClickListener {
                onChangeRuntimeClick(file)
            }

            holder.itemView.setOnLongClickListener {
                onChangeRuntimeClick(file)
                true
            }

            holder.btnLaunchGame.setOnClickListener {
                onLaunchClick(file)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
