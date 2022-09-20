package net.recalstudios.social.models.message

data class MessageContent(
    val attachments: Array<MessageAttachment>,
    var text: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageContent

        if (!attachments.contentEquals(other.attachments)) return false
        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = attachments.contentHashCode()
        result = 31 * result + text.hashCode()
        return result
    }
}
