package net.recalstudios.social

import io.ktor.websocket.*

class Connection(val session: DefaultWebSocketSession, val rooms: Array<Int>)
