package net.recalstudios.social

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.recalstudios.social.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
        configureSockets()
        configureSecurity()
    }.start(wait = true)
}
