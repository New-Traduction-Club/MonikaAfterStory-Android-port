package org.renpy.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.renpy.android.databinding.ActivityAppInfoBinding

class AppInfoActivity : GameWindowActivity() {

    private lateinit var binding: ActivityAppInfoBinding

    private val COMMIT_HASH = "166a970"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setTitle(R.string.title_app_info)

        // Set App Icon
        try {
            val icon = packageManager.getApplicationIcon(packageName)
            binding.ivAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Set Version Text
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        binding.tvVersion.text = getString(R.string.app_info_version, versionName)

        setupHtmlText(binding.tvCreditsPort, getString(R.string.app_info_credits_port))
        setupHtmlText(binding.tvCreditsEs, getString(R.string.app_info_credits_es))
        setupHtmlText(binding.tvCreditsPt, getString(R.string.app_info_credits_pt))
        
        setupHtmlText(binding.tvAckTeamSalvato, getString(R.string.app_info_acknowledgments_team_salvato))
        setupHtmlText(binding.tvAckMasTeam, getString(R.string.app_info_acknowledgments_mas_team))
        setupHtmlText(binding.tvAckRenpy, getString(R.string.app_info_acknowledgments_renpy))
        setupHtmlText(binding.tvAckLattyware, getString(R.string.app_info_acknowledgments_lattyware))

        binding.tvCommit.text = getString(R.string.app_info_commit, COMMIT_HASH)

        binding.cvCommit.setOnClickListener {
            val url = "https://github.com/New-Traduction-Club/MonikaAfterStory-Android-port"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }
    
    private fun setupHtmlText(textView: android.widget.TextView, htmlString: String) {
        textView.text = androidx.core.text.HtmlCompat.fromHtml(
            htmlString, 
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }
}
