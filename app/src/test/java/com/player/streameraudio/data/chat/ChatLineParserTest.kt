package com.player.streameraudio.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatLineParserTest {
    @Test
    fun parsesTaggedMessage() {
        val line = "@badge-info=;color=#FF7F50;display-name=Some\\sOne;mod=0 " +
            ":someone!someone@someone.tmi.twitch.tv PRIVMSG #channel :hola : que tal"
        val message = parseChatLine(line, 7)
        assertEquals(ChatMessage(7, "Some One", "#FF7F50", "hola : que tal"), message)
    }

    @Test
    fun fallsBackToLoginAndNoColor() {
        val message = parseChatLine(":viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :hi", 1)
        assertEquals(ChatMessage(1, "viewer", null, "hi"), message)
    }

    @Test
    fun ignoresOtherLines() {
        assertNull(parseChatLine(":tmi.twitch.tv 001 justinfan123 :Welcome, GLHF!", 1))
        assertNull(parseChatLine("PING :tmi.twitch.tv", 1))
        assertNull(parseChatLine("", 1))
    }
}
