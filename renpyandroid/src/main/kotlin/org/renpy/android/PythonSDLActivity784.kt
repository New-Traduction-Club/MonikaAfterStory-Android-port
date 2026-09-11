package org.renpy.android

import android.os.Bundle

open class PythonSDLActivity784 : PythonSDLActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        isRenpy7Engine = true
        super.onCreate(savedInstanceState)
    }
}
