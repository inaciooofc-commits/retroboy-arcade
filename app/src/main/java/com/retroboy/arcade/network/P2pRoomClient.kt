package com.retroboy.arcade.network

import android.content.Context
import com.retroboy.arcade.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.io.Closeable
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Supabase is only the signaling channel; controller input travels directly over WebRTC. */
class P2pRoomClient(
    context: Context,
    roomCode: String,
    private val isHost: Boolean,
    private val gameHash: String,
    private val coreId: String,
    private val onStatus: (String) -> Unit,
    private val onRemoteInput: (Int) -> Unit
) : Closeable {
    private val appContext = context.applicationContext
    private val room = roomCode.uppercase().filter { it.isLetterOrDigit() }
    private val topic = "realtime:retroboy_${room.lowercase()}"
    private val localId = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient()
    private val closed = AtomicBoolean(false)
    private val refs = AtomicInteger(2)
    private val remoteDescriptionSet = AtomicBoolean(false)
    private val pendingCandidates = Collections.synchronizedList(mutableListOf<IceCandidate>())

    @Volatile private var socket: WebSocket? = null
    @Volatile private var factory: PeerConnectionFactory? = null
    @Volatile private var peer: PeerConnection? = null
    @Volatile private var channel: DataChannel? = null
    @Volatile private var joined = false
    @Volatile private var offerStarted = false
    private var heartbeat: Job? = null
    private var helloLoop: Job? = null

    companion object {
        private const val JOIN_REF = "1"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        @Volatile private var webRtcReady = false

        fun createRoomCode(): String {
            val random = SecureRandom()
            return buildString(8) { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }

        private fun initialize(context: Context) {
            if (webRtcReady) return
            synchronized(P2pRoomClient::class.java) {
                if (!webRtcReady) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
                    )
                    webRtcReady = true
                }
            }
        }
    }

    fun start() {
        check(!closed.get()) { "A sala P2P já foi encerrada." }
        require(room.matches(Regex("[A-Z0-9]{8}"))) { "O código da sala precisa ter 8 caracteres." }
        require(gameHash.isNotBlank()) { "Não foi possível identificar a ROM." }
        require(BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "Configure SUPABASE_URL e SUPABASE_ANON_KEY para habilitar o P2P."
        }

        initialize(appContext)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val ice = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        if (BuildConfig.TURN_URL.isNotBlank()) {
            ice += PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                .setUsername(BuildConfig.TURN_USERNAME)
                .setPassword(BuildConfig.TURN_CREDENTIAL)
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                signal("ice", JSONObject().put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex).put("candidate", candidate.sdp))
            }
            override fun onDataChannel(dataChannel: DataChannel) = attachChannel(dataChannel)
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CHECKING -> status("Negociando conexão direta…")
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> status("ICE conectado • ativando controles…")
                    PeerConnection.IceConnectionState.DISCONNECTED -> status("Conexão interrompida • tentando recuperar…")
                    PeerConnection.IceConnectionState.FAILED -> status("Conexão direta falhou. Esta rede pode exigir TURN.")
                    PeerConnection.IceConnectionState.CLOSED -> status("Conexão encerrada.")
                    else -> Unit
                }
            }
        }) ?: throw IllegalStateException("Não foi possível criar a conexão WebRTC.")

        connectSignaling()
    }

    private fun connectSignaling() {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val socketBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> throw IllegalStateException("SUPABASE_URL inválida.")
        }
        val key = URLEncoder.encode(BuildConfig.SUPABASE_ANON_KEY, "UTF-8")
        val request = Request.Builder()
            .url("\$socketBase/realtime/v1/websocket?apikey=\$key&vsn=1.0.0")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("enabled", false))
                    .put("private", false)
                webSocket.send(JSONObject()
                    .put("topic", topic).put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config))
                    .put("ref", "1").put("join_ref", JOIN_REF).toString())
                status("Sala P2P aberta • aguardando sinalização…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "phx_reply" -> {
                            val payload = message.optJSONObject("payload")
                            if (payload?.optString("status") == "ok") {
                                joined = true
                                status(if (isHost) "Sala criada • aguardando jogador…" else "Entrou na sala • localizando anfitrião…")
                                startHeartbeat()
                                startHelloLoop()
                            } else if (payload?.optString("status") == "error") status("Supabase recusou a sala P2P.")
                        }
                        "broadcast" -> {
                            val payload = message.optJSONObject("payload")
                            if (payload?.optString("event") == "signal") payload.optJSONObject("payload")?.let(::handleSignal)
                        }
                        "phx_error", "phx_close" -> status("Canal de sinalização fechado.")
                    }
                }.onFailure { status("Mensagem P2P inválida: ${it.message ?: "erro"}") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                status("Falha no sinalizador Supabase: ${t.message ?: "sem conexão"}.")
            }
        })
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive && !closed.get()) {
                delay(20_000)
                socket?.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                    .put("payload", JSONObject()).put("ref", refs.getAndIncrement().toString()).toString())
            }
        }
    }

    private fun startHelloLoop() {
        helloLoop?.cancel()
        helloLoop = scope.launch {
            repeat(12) {
                if (!isActive || closed.get() || offerStarted) return@launch
                signal("hello")
                delay(1_000)
            }
        }
    }

    private fun compatible(message: JSONObject): Boolean =
        message.optString("gameHash") == gameHash && message.optString("coreId") == coreId

    private fun handleSignal(message: JSONObject) {
        if (closed.get() || message.optString("sender") == localId) return
        val kind = message.optString("kind")
        if (kind != "leave" && !compatible(message)) {
            status("Jogo ou núcleo diferente. Os dois aparelhos precisam usar os mesmos arquivos.")
            return
        }
        when (kind) {
            "hello" -> {
                if (isHost && !offerStarted) createOffer()
                else if (!isHost) status("Anfitrião encontrado • aguardando oferta…")
            }
            "offer" -> if (!isHost) receiveOffer(message.optString("sdp"))
            "answer" -> if (isHost) receiveAnswer(message.optString("sdp"))
            "ice" -> receiveIce(message)
            "leave" -> { onRemoteInput(0); status("O outro jogador saiu da sala.") }
        }
    }

    private fun createOffer() {
        if (offerStarted || closed.get()) return
        offerStarted = true
        val connection = peer ?: return
        attachChannel(connection.createDataChannel("retroboy-input", DataChannel.Init()))
        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { signal("offer", JSONObject().put("sdp", description.description)) }
                    override fun onSetFailure(error: String) = status("Falha ao preparar oferta: \$error")
                    override fun onCreateSuccess(description: SessionDescription) = Unit
                    override fun onCreateFailure(error: String) = Unit
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = status("Falha ao criar oferta: \$error")
            override fun onSetFailure(error: String) = status("Falha ao criar oferta: \$error")
        }, MediaConstraints())
        status("Oferta criada • aguardando resposta…")
    }

    private fun receiveOffer(sdp: String) {
        val connection = peer ?: return
        connection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet.set(true)
                flushCandidates()
                connection.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) {
                        connection.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() { signal("answer", JSONObject().put("sdp", description.description)) }
                            override fun onSetFailure(error: String) = status("Falha ao preparar resposta: \$error")
                            override fun onCreateSuccess(description: SessionDescription) = Unit
                            override fun onCreateFailure(error: String) = Unit
                        }, description)
                    }
                    override fun onSetSuccess() = Unit
                    override fun onCreateFailure(error: String) = status("Falha ao responder oferta: \$error")
                    override fun onSetFailure(error: String) = status("Falha ao responder oferta: \$error")
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String) = status("Oferta inválida: \$error")
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun receiveAnswer(sdp: String) {
        val connection = peer ?: return
        connection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet.set(true)
                flushCandidates()
                status("Resposta recebida • conectando…")
            }
            override fun onSetFailure(error: String) = status("Resposta inválida: \$error")
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun receiveIce(message: JSONObject) {
        val candidate = IceCandidate(message.optString("sdpMid"), message.optInt("sdpMLineIndex"), message.optString("candidate"))
        if (remoteDescriptionSet.get()) peer?.addIceCandidate(candidate) else pendingCandidates.add(candidate)
    }

    private fun flushCandidates() {
        synchronized(pendingCandidates) {
            pendingCandidates.forEach { peer?.addIceCandidate(it) }
            pendingCandidates.clear()
        }
    }

    private fun attachChannel(value: DataChannel) {
        channel?.unregisterObserver()
        channel?.close()
        channel = value
        value.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                if (value.state() == DataChannel.State.OPEN) status("P2P CONECTADO • CONTROLES ATIVOS")
                if (value.state() == DataChannel.State.CLOSED) {
                    onRemoteInput(0)
                    if (!closed.get()) status("Canal de controles encerrado.")
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) return
                val data = buffer.data.slice().order(ByteOrder.BIG_ENDIAN)
                if (data.remaining() >= 4) onRemoteInput(data.int)
            }
        })
        if (value.state() == DataChannel.State.OPEN) status("P2P CONECTADO • CONTROLES ATIVOS")
    }

    fun sendInput(mask: Int) {
        val current = channel ?: return
        if (closed.get() || current.state() != DataChannel.State.OPEN) return
        val data = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mask)
        data.flip()
        current.send(DataChannel.Buffer(data, true))
    }

    private fun signal(kind: String, extra: JSONObject = JSONObject()) {
        if (!joined || closed.get()) return
        val payload = JSONObject().put("kind", kind).put("sender", localId)
            .put("gameHash", gameHash).put("coreId", coreId)
        val iterator = extra.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            payload.put(key, extra.opt(key))
        }
        socket?.send(JSONObject().put("topic", topic).put("event", "broadcast")
            .put("payload", JSONObject().put("event", "signal").put("payload", payload))
            .put("ref", refs.getAndIncrement().toString()).put("join_ref", JOIN_REF).toString())
    }

    private fun status(message: String) {
        if (!closed.get()) onStatus(message)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        heartbeat?.cancel()
        helloLoop?.cancel()
        runCatching { signal("leave") }
        onRemoteInput(0)
        runCatching { channel?.unregisterObserver(); channel?.close(); channel?.dispose() }
        channel = null
        runCatching { peer?.close(); peer?.dispose() }
        peer = null
        runCatching { factory?.dispose() }
        factory = null
        runCatching { socket?.close(1000, "room closed") }
        socket = null
        scope.cancel()
    }
}
