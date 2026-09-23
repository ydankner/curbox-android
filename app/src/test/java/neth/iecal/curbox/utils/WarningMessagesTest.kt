package neth.iecal.curbox.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class WarningMessagesTest {
    @Test
    fun commasStayInsideAMessage() {
        assertEquals(
            listOf("Yo, go read a book or something <3"),
            WarningMessages.parse("Yo, go read a book or something <3")
        )
    }

    @Test
    fun eachLineIsOneMessage() {
        assertEquals(
            listOf("Take a walk", "Call a friend, maybe"),
            WarningMessages.parse("Take a walk\n\n  Call a friend, maybe  \n")
        )
    }

    @Test
    fun formattingRoundTrips() {
        val messages = listOf("First, with a comma", "Second")
        assertEquals(messages, WarningMessages.parse(WarningMessages.format(messages)))
    }
}
