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
        webSocket("/") {
            // New connection established
            val connectionId = this.hashCode() // Save unique connection ID
            var thisConnection: Connection? = null // Declare empty connection
            println("${Date()} [Connection-$connectionId] INFO  Connection attempt, asking for credentials")

            // Process connection
            try {
                // Ask the client for credentials
                send(Gson().toJson(mapOf("type" to "status", "data" to "auth"))) // Send response
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
                        // TODO: Notify socket of bad data
                        println("${Date()} [Connection-$connectionId] INFO  Received bad data, ignoring: $received")
                        continue
                    }

                    // Process data
                    when (parsed["type"])
                    {
                        "auth" -> {
                            // Authenticate user
                            // TODO: Get rooms of user
                            thisConnection = Connection(this, arrayOf(0, 5, 7, 8))
                            connections += thisConnection
                            println("${Date()} [Connection-$connectionId] INFO  User authenticated, connection accepted (${connections.size} total)")
                        }
                        "message" -> {
                            // Relay message
                            // TODO: Check if user is in room
                            connections.filter { (parsed["room"] as Double).toInt() in it.rooms }.forEach {
                                it.session.send(Gson().toJson(parsed))
                            }

                            // TODO: Store message in DB

                            println("${Date()} [Connection-$connectionId] INFO  Relayed message")
                        }
                        // TODO: Handle deleted messages
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Connection closed
                // TODO: Notify the socket that it closed
                println("${Date()} [Connection-$connectionId] INFO  Connection closed")
                connections -= thisConnection
            }
        }
    }
}
