package org.renpy.android

import android.content.Intent
import android.os.Bundle
import android.view.View

class TestActivity : GameWindowActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dummyView = View(this)
        setContentView(dummyView)
        setTitle("Test Activity")
    }

    override fun startActivity(intent: Intent?) {
        // If its a restore window intent selflaunching, skip it in tests
        // to prevent ActivityScenario from losing track of the activity lifecycle
        if (intent != null && (intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0 && 
            intent.component?.className == this::class.java.name) {
            return
        }
        super.startActivity(intent)
    }
}
