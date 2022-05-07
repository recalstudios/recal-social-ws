package net.recalstudios.social.models

data class DeletePayload(
    val type: String,
    val room: Int,
    val id: Int
)
