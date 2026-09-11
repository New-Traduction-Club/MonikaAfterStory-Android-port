package org.renpy.android

import android.os.Bundle

open class PythonSDLActivity837 : PythonSDLActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        isRenpy8Engine = true
        super.onCreate(savedInstanceState)
    }
}
