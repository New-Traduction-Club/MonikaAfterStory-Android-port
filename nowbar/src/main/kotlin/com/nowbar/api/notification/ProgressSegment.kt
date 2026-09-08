package com.nowbar.api.notification

import android.graphics.drawable.Icon

data class ProgressSegment(
    val startPosition: Float,
    val color: Int,
    val icon: Icon? = null
) {
    init {
        require(!startPosition.isNaN()) { "Segment startPosition must not be NaN" }
        require(startPosition in 0f..1f) { "Segment startPosition must be in [0.0, 1.0]" }
    }
}
