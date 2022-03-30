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
        webSocket("/e") {
            // New connection established
            val connectionId = this.hashCode() // Save unique connection ID
            var thisConnection: Connection // Declare empty connection
            println("${Date()} [Connection-$connectionId] INFO  Connection attempt, asking for credentials")


            // Ask the client for credentials
            send(Gson().toJson(mapOf("type" to "status", "status" to "auth"))) // Send response
            var token: String // Declare token object

            // Listen for incoming data
            for (frame in incoming) {
                frame as? Frame.Text ?: continue // Assure type of data
                val received = frame.readText() // Store incoming data
                val parsed: Map<*, *> // Declare parsed data

                // Try to parse data
                try {
                    parsed = Gson().fromJson(received, Map::class.java)
                } catch (e: Exception) {
                    println("${Date()} [Connection-$connectionId] INFO  Received bad data, ignoring")
                    continue
                }

                // Process data
                when (parsed["type"])
                {
                    "auth" -> {
                        thisConnection = Connection(this, 0)
                        connections += thisConnection
                        println("${Date()} [Connection-$connectionId] INFO  User authenticated, connection accepted (${connections.size} total)")
                    }
                }
            }


            /*
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
                println("User disconnected from room $room")
                connections -= thisConnection
                connections.filter { it.room == room }.forEach {
                    it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "leave")))
                }
            }*/
        }
    }
}
