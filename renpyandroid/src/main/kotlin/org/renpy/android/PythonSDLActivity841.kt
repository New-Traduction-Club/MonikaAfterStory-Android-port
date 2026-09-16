package org.renpy.android

import android.os.Bundle

open class PythonSDLActivity841 : PythonSDLActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        isRenpy841Engine = true
        super.onCreate(savedInstanceState)
    }
}
