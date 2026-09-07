package org.renpy.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExperimentsActivity : GameWindowActivity() {

    private lateinit var rvExperiments: RecyclerView
    private lateinit var tvEmptyState: TextView

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
                rvExperiments.adapter = GamesAdapter(games) { gameFolder ->
                    SoundEffects.playClick(this@ExperimentsActivity)
                    val targetClass = getFreeActivityClass()
                    val intent = Intent(this@ExperimentsActivity, targetClass).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("base_dir", gameFolder.name)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun getFreeActivityClass(): Class<out PythonSDLActivity> {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return PythonSDLActivity2::class.java
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
            if (child.isDirectory && child.name != "monikaafterstory-masl-edition") {
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
        private val onLaunchClick: (File) -> Unit
    ) : RecyclerView.Adapter<GamesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvGameTitle: TextView = view.findViewById(R.id.tvGameTitle)
            val tvGamePath: TextView = view.findViewById(R.id.tvGamePath)
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
            
            holder.btnLaunchGame.setOnClickListener {
                onLaunchClick(file)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
