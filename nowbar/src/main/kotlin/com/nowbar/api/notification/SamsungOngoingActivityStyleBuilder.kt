package com.nowbar.api.notification

import android.app.Notification
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import java.lang.reflect.Method

/**
 * Safe reflection wrapper for Samsung's hidden `Notification.OngoingActivityStyle`.
 *
 * Samsung Voice Recorder uses this framework style for its native ongoing activity
 * notifications. The class is not part of the public Android SDK, so this builder returns
 * `null` when the runtime does not expose it and ignores individual methods that are absent
 * on a particular One UI build.
 */
class SamsungOngoingActivityStyleBuilder internal constructor(
    private val className: String
) {
    constructor() : this(CLASS_NAME)

    private val operations = mutableListOf<Operation>()

    fun chipIcon(icon: Icon): SamsungOngoingActivityStyleBuilder =
        operation("setChipIcon", icon)

    fun chipBackgroundColor(color: Int): SamsungOngoingActivityStyleBuilder =
        operation("setChipBackground", color)

    fun cardIcon(icon: Icon): SamsungOngoingActivityStyleBuilder =
        operation("setCardIcon", icon)

    fun badgeIcon(icon: Icon): SamsungOngoingActivityStyleBuilder =
        operation("setBadge", icon)

    fun cardBackgroundColor(color: Int): SamsungOngoingActivityStyleBuilder =
        operation("setCardBackground", color)

    fun primaryInfo(text: CharSequence): SamsungOngoingActivityStyleBuilder =
        operation("setPrimaryInfo", text)

    fun secondaryInfo(text: CharSequence): SamsungOngoingActivityStyleBuilder =
        operation("setSecondaryInfo", text)

    fun moreInfo(text: CharSequence): SamsungOngoingActivityStyleBuilder =
        operation("setMoreInfo", text)

    fun expandedChipView(remoteViews: RemoteViews): SamsungOngoingActivityStyleBuilder =
        operation("setExpandedChipView", remoteViews)

    fun customCardViewCenterUi(remoteViews: RemoteViews): SamsungOngoingActivityStyleBuilder =
        operation("setCustomCardViewCenterUI", remoteViews)

    fun customExpandedCardView(remoteViews: RemoteViews): SamsungOngoingActivityStyleBuilder =
        operation("setCustomExpandedCardView", remoteViews)

    fun action(action: Notification.Action): SamsungOngoingActivityStyleBuilder =
        operation("addAction", action)

    fun actions(actions: List<Notification.Action>): SamsungOngoingActivityStyleBuilder = apply {
        actions.forEach(::action)
    }

    /**
     * Returns a Samsung native style when available, or `null` on unsupported runtimes.
     */
    fun build(): Notification.Style? =
        buildWithReport().style

    /**
     * Builds the hidden Samsung style and reports which reflection methods were applied.
     *
     * Use this on real Samsung devices when diagnosing One UI drift: the hidden style can
     * exist while individual methods are renamed, removed, or blocked.
     */
    fun buildWithReport(): SamsungOngoingActivityStyleBuildResult {
        val requestedMethods = operations.map { it.methodName }
        val classAvailable = isClassAvailable(className)
        val style = createStyleInstance()
        if (style == null) {
            return SamsungOngoingActivityStyleBuildResult(
                style = null,
                report = SamsungOngoingActivityStyleReport(
                    className = className,
                    classAvailable = classAvailable,
                    styleCreated = false,
                    requestedMethods = requestedMethods,
                    appliedMethods = emptyList(),
                    missingMethods = requestedMethods,
                    failedMethods = emptyList()
                )
            )
        }

        val applied = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val failed = mutableListOf<String>()
        operations.forEach { operation ->
            when (invokeOptional(style, operation.methodName, operation.argument)) {
                InvocationResult.APPLIED -> applied += operation.methodName
                InvocationResult.MISSING -> missing += operation.methodName
                InvocationResult.FAILED -> failed += operation.methodName
            }
        }
        return SamsungOngoingActivityStyleBuildResult(
            style = style,
            report = SamsungOngoingActivityStyleReport(
                className = className,
                classAvailable = classAvailable,
                styleCreated = true,
                requestedMethods = requestedMethods,
                appliedMethods = applied,
                missingMethods = missing,
                failedMethods = failed
            )
        )
    }

    private fun operation(
        methodName: String,
        argument: Any
    ): SamsungOngoingActivityStyleBuilder = apply {
        operations += Operation(methodName, argument)
    }

    private fun createStyleInstance(): Notification.Style? =
        runCatching {
            val klass = Class.forName(className)
            val instance = klass.getDeclaredConstructor().apply {
                isAccessible = true
            }.newInstance()
            instance as? Notification.Style
        }.getOrNull()

    private fun invokeOptional(
        target: Any,
        methodName: String,
        argument: Any
    ): InvocationResult {
        val method = target.javaClass.allInstanceMethods()
            .firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].accepts(argument)
            }
            ?: return InvocationResult.MISSING

        return runCatching {
            method.isAccessible = true
            method.invoke(target, argument)
        }.fold(
            onSuccess = { InvocationResult.APPLIED },
            onFailure = { InvocationResult.FAILED }
        )
    }

    private data class Operation(
        val methodName: String,
        val argument: Any
    )

    private enum class InvocationResult {
        APPLIED,
        MISSING,
        FAILED
    }

    companion object {
        const val CLASS_NAME = "android.app.Notification\$OngoingActivityStyle"

        @JvmStatic
        fun isAvailable(): Boolean =
            isClassAvailable(CLASS_NAME)

        private fun isClassAvailable(className: String): Boolean =
            runCatching {
                Class.forName(className).getDeclaredConstructor()
            }.isSuccess
    }
}

data class SamsungOngoingActivityStyleBuildResult(
    val style: Notification.Style?,
    val report: SamsungOngoingActivityStyleReport
)

data class SamsungOngoingActivityStyleReport(
    val className: String,
    val classAvailable: Boolean,
    val styleCreated: Boolean,
    val requestedMethods: List<String>,
    val appliedMethods: List<String>,
    val missingMethods: List<String>,
    val failedMethods: List<String>
) {
    val complete: Boolean
        get() = styleCreated && missingMethods.isEmpty() && failedMethods.isEmpty()

    fun toDisplayString(): String = buildString {
        appendLine("Hidden style class: $className")
        appendLine("Class available: $classAvailable")
        appendLine("Style created: $styleCreated")
        appendLine("Requested methods: ${requestedMethods.joinToString().ifBlank { "none" }}")
        appendLine("Applied methods: ${appliedMethods.joinToString().ifBlank { "none" }}")
        appendLine("Missing methods: ${missingMethods.joinToString().ifBlank { "none" }}")
        append("Failed methods: ")
        append(failedMethods.joinToString().ifBlank { "none" })
    }
}

private fun Class<*>.allInstanceMethods(): Sequence<Method> =
    generateSequence(this) { type -> type.superclass }
        .flatMap { type -> type.declaredMethods.asSequence() }

private fun Class<*>.accepts(argument: Any): Boolean {
    val parameterType = if (isPrimitive) boxedPrimitiveType() else this
    return parameterType.isAssignableFrom(argument.javaClass)
}

private fun Class<*>.boxedPrimitiveType(): Class<*> =
    when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> this
    }
