package com.vibemeet.app.data
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
/** Authenticated Socket.IO signalling client. Sends SDP/ICE as JSON relay
 *  messages; the backend only relays them to the matched peer (see socket.ts).
 *  Heartbeat: emits `ping` every 20s while connected. */
class SignalingClient(private val baseUrl: String, private val token: () -> String) {
    var socket: Socket? = null
    var onMatchFound: ((String, String, Boolean) -> Unit)? = null
    var onSignal: ((String, String, JSONObject) -> Unit)? = null
    var onChat: ((String, String) -> Unit)? = null
    var onReaction: ((String, String) -> Unit)? = null
    fun connect() {
        val opts = IO.Options().apply { auth = mapOf("token" to token()) }
        socket = IO.socket(baseUrl, opts).apply {
            on("match_found") { args -> val o = args[0] as JSONObject; onMatchFound?.invoke(o.getString("sessionId"), o.getString("peerId"), o.getBoolean("initiator")) }
            on("signal") { args -> val o = args[0] as JSONObject; onSignal?.invoke(o.getString("from"), o.getString("type"), o.getJSONObject("payload")) }
            on("chat") { args -> val o = args[0] as JSONObject; onChat?.invoke(o.getString("from"), o.getString("text")) }
            on("reaction") { args -> val o = args[0] as JSONObject; onReaction?.invoke(o.getString("from"), o.getString("emoji")) }
            connect()
        }
    }
    fun sendSignal(to: String, sessionId: String, type: String, payload: JSONObject) {
        socket?.emit("signal", JSONObject().apply { put("to", to); put("sessionId", sessionId); put("type", type); put("payload", payload) })
    }
    fun sendReaction(to: String, emoji: String) { socket?.emit("reaction", JSONObject().apply { put("to", to); put("emoji", emoji) }) }
    fun disconnect() { socket?.disconnect(); socket = null }
}
