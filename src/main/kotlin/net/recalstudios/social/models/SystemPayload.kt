package net.recalstudios.social.models

data class SystemPayload(
    val type: String,
    val room: Int,
    val action: String,
    val content: String
)
