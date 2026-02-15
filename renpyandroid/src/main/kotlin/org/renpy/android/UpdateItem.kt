package org.renpy.android

data class UpdateItem(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val versionName: String,
    val url: String,
    val type: String, // "language" or "system"
    val targetFile: String
)
