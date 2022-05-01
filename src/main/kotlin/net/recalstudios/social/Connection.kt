package net.recalstudios.social

import io.ktor.server.websocket.*

class Connection(val session: DefaultWebSocketSession, val rooms: Array<Int>)
