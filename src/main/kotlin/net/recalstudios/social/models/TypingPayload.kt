package net.recalstudios.social.models

data class TypingPayload(
    val type: String,
    val room: Int,
    val user: Int,
    val typing: Boolean
)
