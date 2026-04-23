package org.renpy.android

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import org.renpy.android.databinding.ActivityExtraContentBinding

class ExtraContentActivity : GameWindowActivity() {

    private lateinit var binding: ActivityExtraContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtraContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.extra_content_title)

        val items = listOf(
            DesktopShortcut(
                R.string.extra_content_option_install_sprites,
                android.R.drawable.ic_input_add,
                "install_sprites"
            )
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = DesktopItemAdapter(items) { shortcut ->
            SoundEffects.playClick(this)
            if (shortcut.actionId == "install_sprites") {
                startActivity(Intent(this, SpritepackInstallerActivity::class.java))
            }
        }
    }
}
