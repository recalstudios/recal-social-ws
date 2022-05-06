package net.recalstudios.social.models.message

data class Message(
    val id: Int,
    val type: String,
    val room: Int,
    val author: Int,
    val content: MessageContent,
    val timestamp: String
)
