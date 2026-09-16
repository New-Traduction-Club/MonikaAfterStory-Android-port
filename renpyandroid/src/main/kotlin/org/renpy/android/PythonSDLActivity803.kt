package org.renpy.android

import android.os.Bundle

open class PythonSDLActivity803 : PythonSDLActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        isRenpy803Engine = true
        super.onCreate(savedInstanceState)
    }
}
