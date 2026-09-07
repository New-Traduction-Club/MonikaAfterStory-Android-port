package org.renpy.android

import android.content.Intent

class TestFileExplorerActivity : FileExplorerActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView?.itemAnimator = null
    }

    fun callOnNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun startActivity(intent: Intent?) {
        if (intent != null && (intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0 && 
            intent.component?.className == this::class.java.name) {
            return
        }
        super.startActivity(intent)
    }

    override fun onDestroy() {
        try {
            val delegateField = FileExplorerActivity::class.java.getDeclaredField("viewModel\$delegate")
            delegateField.isAccessible = true
            val lazyValue = delegateField.get(this) as Lazy<*>
            val viewModel = lazyValue.value as FileExplorerViewModel
            viewModel.currentDir.removeObservers(this)
            viewModel.files.removeObservers(this)
            viewModel.statusMessage.removeObservers(this)
            viewModel.hasClipboard.removeObservers(this)
        } catch (e: Exception) {}
        try {
            val onDestroyMethod = android.app.Activity::class.java.getDeclaredMethod("onDestroy")
            onDestroyMethod.isAccessible = true
            onDestroyMethod.invoke(this)
        } catch (e: Exception) {
            super.onDestroy()
        }
    }

    override fun finish() {
        try {
            val finishMethod = android.app.Activity::class.java.getDeclaredMethod("finish")
            finishMethod.isAccessible = true
            finishMethod.invoke(this)
        } catch (e: Exception) {
            super.finish()
        }
    }
}
