package neth.iecal.curbox.utils

/**
 * The warning screen shows one of several custom messages at random. In the editor each line
 * is one message, so a message can contain commas and other punctuation.
 */
object WarningMessages {
    fun parse(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun format(messages: List<String>): String = messages.joinToString("\n")
}
