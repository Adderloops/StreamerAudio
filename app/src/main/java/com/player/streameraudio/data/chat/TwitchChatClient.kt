package com.player.streameraudio.data.chat

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(
    val id: Long,
    val name: String,
    /** "#RRGGBB" as sent by Twitch, or null when the user has no name colour. */
    val color: String?,
    val text: String
)

/** Parses one IRC line; returns null for anything that is not a chat message. */
internal fun parseChatLine(line: String, id: Long): ChatMessage? {
    var rest = line.trim()
    var tags = ""
    if (rest.startsWith("@")) {
        val space = rest.indexOf(' ')
        if (space < 0) return null
        tags = rest.substring(1, space)
        rest = rest.substring(space + 1)
    }
    if (!rest.startsWith(":")) return null
    val prefixEnd = rest.indexOf(' ')
    if (prefixEnd < 0) return null
    val prefix = rest.substring(1, prefixEnd)
    rest = rest.substring(prefixEnd + 1)
    if (!rest.startsWith("PRIVMSG ")) return null
    val textStart = rest.indexOf(" :")
    if (textStart < 0) return null
    var text = rest.substring(textStart + 2)
    if (text.startsWith("\u0001ACTION ")) text = text.removePrefix("\u0001ACTION ").removeSuffix("\u0001")
    if (text.isBlank()) return null

    val tagMap = tags.split(';').mapNotNull { entry ->
        val eq = entry.indexOf('=')
        if (eq <= 0) null else entry.substring(0, eq) to entry.substring(eq + 1)
    }.toMap()
    val name = tagMap["display-name"]?.replace("\\s", " ")?.trim()?.takeIf { it.isNotEmpty() }
        ?: prefix.substringBefore('!')
    val color = tagMap["color"]?.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }
    return ChatMessage(id, name, color, text)
}

/** Read-only, anonymous connection to a channel's Twitch chat. */
@Singleton
class TwitchChatClient @Inject constructor(okHttpClient: OkHttpClient) {
    private val client = okHttpClient.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val nextId = AtomicLong()

    /** Emits messages until the connection drops; the flow then completes or fails. */
    fun messages(channel: String): Flow<ChatMessage> = callbackFlow {
        val login = channel.trim().lowercase()
        val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CAP REQ :twitch.tv/tags")
                webSocket.send("NICK justinfan${(10000..99999).random()}")
                webSocket.send("JOIN #$login")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split("\r\n").forEach { line ->
                    if (line.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                    } else {
                        parseChatLine(line, nextId.incrementAndGet())?.let { trySend(it) }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })
        awaitClose { socket.cancel() }
    }
}
