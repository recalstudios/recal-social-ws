package net.recalstudios.social.plugins

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import net.recalstudios.social.Connection
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import javax.net.ssl.X509TrustManager

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        val connections = Collections.synchronizedSet<Connection>(LinkedHashSet()) // List of all connections to the websocket
        val client = HttpClient(CIO) {
            engine {
                https {
                    trustManager = object: X509TrustManager { // Disable SSL verification
                        override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                        override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    }
                }
            }
        }
        webSocket("/") { // websocketSession
            // New connection established
            val connectionId = this.hashCode() // Save unique connection ID
            var thisConnection: Connection? = null // Declare empty connection
            println("${Date()} [Connection-$connectionId] INFO  Connection attempt, asking for credentials")

            // Process connection
            try {
                // Ask the client for credentials
                send(Gson().toJson(mapOf("type" to "status", "data" to "auth"))) // Send response
                var token: String? = null // Declare token object

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
                    when (parsed["type"]) {
                        "auth" -> {
                            // Store token
                            token = parsed["token"] as String

                            // Declare empty array of rooms
                            var rooms = emptyArray<Int>()

                            // Fetch list of rooms associated with the user
                            val response: HttpResponse = client.get("https://api.social.recalstudios.net/user/rooms") {
                                headers {
                                    append(HttpHeaders.Authorization, token)
                                }
                            }

                            // Parse the response from the API
                            val parsedResponse = Gson().fromJson(response.body<String>(), Array::class.java)
                            for (room in parsedResponse) {
                                room as Map<*, *>
                                rooms += (room["id"] as Double).toInt()
                            }

                            // Store data in list of connections
                            thisConnection = Connection(this, rooms)
                            connections += thisConnection
                            println("${Date()} [Connection-$connectionId] INFO  User authenticated to ${rooms.size} rooms, connection accepted (${connections.size} total)")
                        }
                        "message" -> {
                            if (token == null) {
                                // TODO: Notify client it is not authenticated
                            } else {
                                // Relay message to clients in the relevant room
                                connections.filter { (parsed["room"] as Double).toInt() in it.rooms }.forEach {
                                    it.session.send(Gson().toJson(parsed))
                                }

                                // Send message to API
                                val response: HttpResponse = client.post("https://api.social.recalstudios.net/chat/room/message/save") {
                                    headers {
                                        append(HttpHeaders.Authorization, token)
                                    }
                                    setBody(Gson().toJson(mapOf("data" to parsed["content"], "chatroomId" to parsed["room"])))
                                }

                                println("${Date()} [Connection-$connectionId] INFO  Relayed message")
                            }
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
