package net.recalstudios.social.plugins

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import net.recalstudios.social.models.*
import net.recalstudios.social.models.message.Message
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import javax.net.ssl.X509TrustManager

const val api = "https://api.social.recalstudios.net" // URL to the API
val connections = Collections.synchronizedSet<Connection>(LinkedHashSet()) // List of all connections to the websocket
// This gives a weak warning for unchecked nullability issues, but breaks when specifying type explicitly

// Create HttpClient without SSL verification for API communication
val client = HttpClient(CIO) {
    engine {
        https {
            trustManager = object: X509TrustManager {
                override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            }
        }
    }
}

// Allow TypeToken to be used when parsing JSON
inline fun <reified T> Gson.fromJson(json: String): T = fromJson(json, object: TypeToken<T>() {}.type)

// Configure the WebSocket
fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/") { // websocketSession
            // New connection established
            // Declare values
            val connectionId = this.hashCode() // Save unique connection ID
            var thisConnection: Connection? = null // Declare empty connection
            var token: String? = null // Declare token object

            // Ask the client for credentials
            send(Gson().toJson(Payload("status", "auth"))) // Send response
            println("${Date()} [Connection-$connectionId] INFO  Connection attempt, asking for credentials")

            // Listen for incoming data
            try {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue // Assure data is text
                    val data = frame.readText() // Store incoming data
                    var type: String // Declare the type of message received

                    // Find message type
                    try {
                        val payload: Payload = Gson().fromJson(data)
                        type = payload.type
                    } catch (e: Exception) {
                        // Notify client of bad data
                        send(Gson().toJson(Payload("invalid", data)))
                        println("${Date()} [Connection-$connectionId] INFO  Received bad data, ignoring: $data")
                        continue
                    }

                    // Process data
                    when (type) {
                        "auth" -> {
                            // Get payload
                            val payload: AuthPayload = Gson().fromJson(data)

                            // Store token
                            token = payload.token

                            // Declare empty array of rooms
                            var rooms = emptyArray<Int>()

                            // Fetch list of rooms associated with the user
                            val response: HttpResponse = client.get("$api/user/rooms") {
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

                            // Notify client it has been authenticated
                            send(Gson().toJson(Payload("status", "ok")))

                            // Store data in list of connections
                            thisConnection = Connection(this, rooms)
                            connections += thisConnection
                            println("${Date()} [Connection-$connectionId] INFO  User authenticated to ${rooms.size} rooms, connection accepted (${connections.size} total)")
                        }
                        "message" -> {
                            // Get payload
                            val payload: Message = Gson().fromJson(data)

                            // Check if session is authenticated
                            if (token == null) {
                                // Notify client it is not authenticated
                                send(Gson().toJson(Payload("status", "auth")))
                                println("${Date()} [Connection-$connectionId] INFO  Tried sending message without auth, ignoring")
                            } else {
                                // Sanitize html tags
                                payload.content.text = payload.content.text.replace("<", "&lt;")

                                // Send message to API
                                val response: HttpResponse = client.post("$api/chat/room/message/save") {
                                    headers {
                                        append(HttpHeaders.Authorization, token)
                                        append(HttpHeaders.ContentType, "application/json")
                                    }
                                    setBody(Gson().toJson(payload))
                                }

                                // Relay message to clients in the relevant room
                                connections.filter { payload.room in it.rooms }.forEach {
                                    it.session.send(response.body<String>())
                                }

                                println("${Date()} [Connection-$connectionId] INFO  Relayed message")
                            }
                        }
                        "delete" -> {
                            // Get payload
                            val payload: DeletePayload = Gson().fromJson(data)

                            // Check if session is authenticated
                            if (token == null) {
                                // Notify client it is not authenticated
                                send(Gson().toJson(Payload("status", "auth")))
                                println("${Date()} [Connection-$connectionId] INFO  Tried deleting message without auth, ignoring")
                            } else {
                                // Send message to API
                                val response: HttpResponse = client.post("$api/chat/room/message/delete") {
                                    headers {
                                        append(HttpHeaders.Authorization, token)
                                        append(HttpHeaders.ContentType, "application/json")
                                    }
                                    setBody(Gson().toJson(object {
                                        val messageId = payload.id
                                    }))
                                }

                                // Relay message to clients in the relevant room if the request was successful
                                if (response.body<String>().toBoolean()) {
                                    connections.filter { payload.room in it.rooms }.forEach {
                                        it.session.send(Gson().toJson(payload))
                                    }
                                }

                                println("${Date()} [Connection-$connectionId] INFO  Deleted message ${payload.id}")
                            }
                        }
                        "system" -> {
                            // Get payload
                            val payload: SystemPayload = Gson().fromJson(data)

                            // Check if session is authenticated
                            if (token == null) {
                                // Notify client it is not authenticated
                                send(Gson().toJson(Payload("status", "auth")))
                                println("${Date()} [Connection-$connectionId] INFO  Tried sending system message without auth, ignoring")
                            } else {
                                // Relay message to clients in the relevant room
                                connections.filter { payload.room in it.rooms }.forEach {
                                    it.session.send(Gson().toJson(payload))
                                }

                                println("${Date()} [Connection-$connectionId] INFO  Relayed system message")
                            }
                        }
                        "typing" -> {
                            // Get payload
                            val payload: TypingPayload = Gson().fromJson(data)

                            // Check if session is authenticated
                            if (token == null) {
                                // Notify client it is not authenticated
                                send(Gson().toJson(Payload("status", "auth")))
                                println("${Date()} [Connection-$connectionId] INFO  Tried sending typing status without auth, ignoring")
                            } else {
                                // Relay message to clients in the relevant room
                                connections.filter { payload.room in it.rooms }.forEach {
                                    it.session.send(Gson().toJson(payload))
                                }

                                println("${Date()} [Connection-$connectionId] INFO  Relayed typing status")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Connection closed
                println("${Date()} [Connection-$connectionId] INFO  Connection closed")
                connections -= thisConnection // Remove from memory
            }
        }
    }
}
