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
        webSocket("/{room}") {
            val room = call.parameters["room"]!!
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
                    val received = frame.readText()

                    connections.filter { it.room == room }.forEach {
                        it.session.send(received)
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
    }
}
