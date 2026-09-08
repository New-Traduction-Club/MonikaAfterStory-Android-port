package com.nowbar.api.notification

import android.app.Notification
import android.os.Build
import android.text.Annotation
import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * Android 17+ semantic title styles used by Live Updates.
 *
 * The constants mirror android.app.Notification.SEMANTIC_STYLE_* so callers can
 * target the API while this library still compiles against older SDK platforms.
 */
enum class LiveUpdateSemanticStyle(val platformValue: Int) {
    UNSPECIFIED(0),
    INFO(1),
    SAFE(2),
    CAUTION(3),
    DANGER(4)
}

object LiveUpdateTextStyler {
    @JvmStatic
    fun styleTitle(
        title: CharSequence,
        semanticStyle: LiveUpdateSemanticStyle?
    ): CharSequence {
        if (semanticStyle == null ||
            semanticStyle == LiveUpdateSemanticStyle.UNSPECIFIED ||
            Build.VERSION.SDK_INT < 37
        ) {
            return title
        }

        return runCatching {
            val annotation = Notification::class.java
                .getMethod("createSemanticStyleAnnotation", Int::class.javaPrimitiveType)
                .invoke(null, semanticStyle.platformValue) as Annotation

            SpannableStringBuilder().append(
                title,
                annotation,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }.getOrDefault(title)
    }
}
