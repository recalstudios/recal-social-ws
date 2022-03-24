package net.recalstudios.social.plugins

import com.google.gson.Gson
import net.recalstudios.social.Connection
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.util.*
import kotlin.collections.LinkedHashSet

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val connections = Collections.synchronizedSet<Connection>(LinkedHashSet())
        webSocket("/room/{room}") {
            val room = call.parameters["room"]!!
            if (connections.any { it.room == room }) {
                println("New connection to room $room")
                val thisConnection = Connection(this, room)
                connections += thisConnection

                try {
                    send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to true, "room" to room)))
                    connections.filter { it.room == room }.forEach {
                        it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "join")))
                    }
                    for (frame in incoming) {
                        frame as?Frame.Text ?: continue
                        val received = Gson().fromJson(frame.readText(), Map::class.java)
                        var `return`: Map<Any, Any>

                        `return` = mapOf("type" to "response", "success" to true)
                        val send = mapOf("type" to "remote") + received

                        send(Gson().toJson(`return`))
                        connections.filter { it.room == room }.forEach {
                            it.session.send(Gson().toJson(send))
                        }
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    connections.filter { it.room == room }.forEach {
                        it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "leave")))
                    }
                    println("User disconnected from room $room")
                    connections -= thisConnection
                }
            }
            else send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to false, "reason" to "Invalid game code")))
        }
        webSocket("/new") {
            var room: String
            do {
                room = createRoomCode()
            } while (connections.any { it.room == room })

            println("New room created: $room")
            println("New connection to room $room")
            val thisConnection = Connection(this, room)
            connections += thisConnection

            try {
                send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to true, "room" to room)))
                for (frame in incoming) {
                    frame as?Frame.Text ?: continue
                    val received = Gson().fromJson(frame.readText(), Map::class.java)
                    var `return`: Map<Any, Any>

                    `return` = mapOf("type" to "response", "success" to true)
                    val send = mapOf("type" to "remote") + received

                    send(Gson().toJson(`return`))
                    connections.filter { it.room == room }.forEach {
                        it.session.send(Gson().toJson(send))
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                connections.filter { it.room == room }.forEach {
                    it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "leave")))
                }
                println("User disconnected from room $room")
                println("Room deleted: $room")
                connections -= thisConnection
            }
        }
    }
}

fun createRoomCode(): String {
    val chars = 'A'..'Z'
    return (1..4)
        .map { chars.random() }
        .joinToString("")
}
