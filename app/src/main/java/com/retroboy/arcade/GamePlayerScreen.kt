package com.retroboy.arcade

import android.content.Intent
import android.graphics.PixelFormat
import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retroboy.arcade.emulation.LibretroGameSession
import com.retroboy.arcade.network.P2pRoomClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private val PlayerBg = Color(0xFF070918)
private val PlayerPanel = Color(0xFF111631)
private val PlayerCyan = Color(0xFF00EFFF)
private val PlayerPurple = Color(0xFF8F5BFF)
private val PlayerMuted = Color(0xFF93A4C8)

@Composable
fun GamePlayerScreen(
    title: String,
    coreId: String,
    corePath: String,
    gamePath: String,
    systemDirectory: String,
    saveDirectory: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionRef = remember { mutableStateOf<LibretroGameSession?>(null) }
    var status by remember { mutableStateOf("Preparando o núcleo…") }
    var paused by remember { mutableStateOf(false) }
    var surfaceReady by remember { mutableStateOf(false) }
    var showP2pDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var roomCode by remember { mutableStateOf("") }
    var p2pStatus by remember { mutableStateOf("OFFLINE") }
    var p2pClient by remember { mutableStateOf<P2pRoomClient?>(null) }
    var p2pHost by remember { mutableStateOf(true) }
    var remoteMask by remember { mutableStateOf(0) }
    var fpsLabel by remember { mutableStateOf("--") }
    var frameCount by remember { mutableStateOf(0L) }
    var gameHash by remember { mutableStateOf("") }

    val gameExtension = gamePath.substringAfterLast('.', "").lowercase()
    val gameAspectRatio = when (gameExtension) {
        "gba", "bin" -> 3f / 2f
        "gb", "gbc" -> 10f / 9f
        else -> 4f / 3f
    }
    val stateFile = remember(gamePath, saveDirectory) {
        File(saveDirectory, safeStateFileName(File(gamePath).nameWithoutExtension))
    }
    var hasSaveState by remember { mutableStateOf(stateFile.isFile) }

    LaunchedEffect(gamePath) {
        gameHash = withContext(Dispatchers.IO) { md5File(File(gamePath)) }
    }

    LaunchedEffect(sessionRef.value) {
        while (isActive) {
            sessionRef.value?.let { session ->
                fpsLabel = String.format(Locale.US, "%.1f", session.currentFps())
                frameCount = session.frameCount()
            }
            delay(1000)
        }
    }

    val attachP2pToSession: (LibretroGameSession) -> Unit = { session ->
        session.setNetworkPlayerPort(if (p2pHost) 0 else 1)
        session.setRemoteInputMask(remoteMask)
        val activeClient = p2pClient
        session.onLocalInputChanged = activeClient?.let { client ->
            { mask -> client.sendInput(mask) }
        }
    }

    val startP2p: (Boolean, String) -> Unit = { host, code ->
        val normalizedCode = code.uppercase().filter { it.isLetterOrDigit() }
        if (gameHash.isBlank()) {
            p2pStatus = "Aguarde a identificação da ROM…"
        } else if (normalizedCode.length != 8) {
            p2pStatus = "O código da sala precisa ter 8 caracteres."
        } else {
            p2pClient?.close()
            p2pHost = host
            roomCode = normalizedCode
            remoteMask = 0
            sessionRef.value?.setRemoteInputMask(0)
            sessionRef.value?.setNetworkPlayerPort(if (host) 0 else 1)
            val client = P2pRoomClient(
                context = context,
                roomCode = normalizedCode,
                isHost = host,
                gameHash = gameHash,
                coreId = coreId,
                onStatus = { message ->
                    scope.launch(Dispatchers.Main) { p2pStatus = message }
                },
                onRemoteInput = { mask ->
                    sessionRef.value?.setRemoteInputMask(mask)
                    scope.launch(Dispatchers.Main) { remoteMask = mask }
                }
            )
            p2pClient = client
            sessionRef.value?.onLocalInputChanged = { mask -> client.sendInput(mask) }
            runCatching { client.start() }
                .onSuccess { p2pStatus = if (host) "Criando sala…" else "Entrando na sala…" }
                .onFailure { error ->
                    client.close()
                    p2pClient = null
                    sessionRef.value?.onLocalInputChanged = null
                    sessionRef.value?.setRemoteInputMask(0)
                    sessionRef.value?.setNetworkPlayerPort(0)
                    p2pStatus = error.message ?: "Não foi possível iniciar P2P."
                }
        }
    }

    val closeGame: () -> Unit = {
        p2pClient?.close()
        p2pClient = null
        sessionRef.value?.onLocalInputChanged = null
        sessionRef.value?.setRemoteInputMask(0)
        sessionRef.value?.setNetworkPlayerPort(0)
        sessionRef.value?.close()
        sessionRef.value = null
        onClose()
    }

    BackHandler(enabled = true) { closeGame() }

    DisposableEffect(Unit) {
        onDispose {
            p2pClient?.close()
            sessionRef.value?.onLocalInputChanged = null
            sessionRef.value?.setRemoteInputMask(0)
            sessionRef.value?.setNetworkPlayerPort(0)
            sessionRef.value?.close()
            sessionRef.value = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(PlayerBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = closeGame) {
                Icon(Icons.Rounded.Close, contentDescription = "Fechar jogo", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = if (status.startsWith("ERRO")) Color(0xFFFF6B7A) else PlayerCyan, fontSize = 9.sp)
            }
            IconButton(onClick = { showP2pDialog = true }) {
                Icon(Icons.Rounded.Groups, contentDescription = "Multiplayer P2P", tint = if (p2pStatus.contains("CONECTADO")) PlayerCyan else PlayerMuted)
            }
            IconButton(onClick = {
                paused = !paused
                sessionRef.value?.pause(paused)
            }) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (paused) "Retomar" else "Pausar", tint = PlayerCyan)
            }
            IconButton(onClick = { sessionRef.value?.reset() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar jogo", tint = PlayerCyan)
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PlayerPanel).padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$fpsLabel FPS  •  $frameCount FRAMES", color = PlayerCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(p2pStatus, color = if (p2pStatus.contains("CONECTADO")) PlayerCyan else PlayerMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { sessionRef.value?.saveState() }
                    if (bytes != null) {
                        withContext(Dispatchers.IO) {
                            stateFile.parentFile?.mkdirs()
                            stateFile.writeBytes(bytes)
                        }
                        hasSaveState = true
                        status = "SAVE STATE SALVO"
                    } else {
                        status = "NÚCLEO NÃO DISPONIBILIZA SAVE STATE"
                    }
                }
            }) { Text("SALVAR ESTADO", color = PlayerCyan, fontSize = 9.sp) }
            TextButton(enabled = hasSaveState, onClick = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { stateFile.takeIf { it.isFile }?.readBytes() }
                    val loaded = bytes?.let { state -> withContext(Dispatchers.IO) { sessionRef.value?.loadState(state) } } == true
                    status = if (loaded) "SAVE STATE CARREGADO" else "NÃO FOI POSSÍVEL CARREGAR"
                }
            }) { Text("CARREGAR", color = if (hasSaveState) PlayerCyan else PlayerMuted, fontSize = 9.sp) }
        }

        Box(
            Modifier.fillMaxWidth().aspectRatio(gameAspectRatio).clip(RoundedCornerShape(12.dp))
                .background(Color.Black).border(1.dp, PlayerPurple.copy(alpha = .45f), RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        holder.setFormat(PixelFormat.RGBA_8888)
                        setOnKeyListener { _, keyCode, event ->
                            val buttonId = when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> 4
                                KeyEvent.KEYCODE_DPAD_DOWN -> 5
                                KeyEvent.KEYCODE_DPAD_LEFT -> 6
                                KeyEvent.KEYCODE_DPAD_RIGHT -> 7
                                KeyEvent.KEYCODE_BUTTON_B -> 0
                                KeyEvent.KEYCODE_BUTTON_Y -> 1
                                KeyEvent.KEYCODE_BUTTON_SELECT -> 2
                                KeyEvent.KEYCODE_BUTTON_START -> 3
                                KeyEvent.KEYCODE_BUTTON_A -> 8
                                KeyEvent.KEYCODE_BUTTON_X -> 9
                                KeyEvent.KEYCODE_BUTTON_L1 -> 10
                                KeyEvent.KEYCODE_BUTTON_R1 -> 11
                                KeyEvent.KEYCODE_BUTTON_L2 -> 12
                                KeyEvent.KEYCODE_BUTTON_R2 -> 13
                                else -> -1
                            }
                            if (buttonId >= 0) {
                                if (event.action == KeyEvent.ACTION_DOWN) sessionRef.value?.setButton(buttonId, true)
                                if (event.action == KeyEvent.ACTION_UP) sessionRef.value?.setButton(buttonId, false)
                                true
                            } else false
                        }
                        requestFocus()
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                if (sessionRef.value != null) return
                                surfaceReady = true
                                status = "Inicializando emulador…"
                                val targetSurface = holder.surface
                                scope.launch {
                                    try {
                                        val session = withContext(Dispatchers.IO) {
                                            LibretroGameSession(
                                                corePath = corePath,
                                                contentPath = gamePath,
                                                systemDirectory = systemDirectory,
                                                saveDirectory = saveDirectory,
                                                surface = targetSurface
                                            )
                                        }
                                        if (targetSurface.isValid) {
                                            sessionRef.value = session
                                            attachP2pToSession(session)
                                            status = "EM EXECUÇÃO"
                                        } else {
                                            session.close()
                                            status = "ERRO: superfície de vídeo encerrada."
                                        }
                                    } catch (error: Exception) {
                                        status = "ERRO: " + (error.message ?: "não foi possível iniciar o jogo.")
                                    }
                                }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                                sessionRef.value?.close()
                                sessionRef.value = null
                            }
                        })
                    }
                }
            )
            if (!surfaceReady) {
                Text("AGUARDANDO TELA DE JOGO", color = PlayerMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TouchControl("▲", 4, sessionRef, Modifier.size(46.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TouchControl("◀", 6, sessionRef, Modifier.size(46.dp))
                    TouchControl("▼", 5, sessionRef, Modifier.size(46.dp))
                    TouchControl("▶", 7, sessionRef, Modifier.size(46.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TouchControl("Y", 1, sessionRef, Modifier.size(42.dp), PlayerPurple)
                    TouchControl("X", 9, sessionRef, Modifier.size(42.dp), PlayerPurple)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TouchControl("L", 10, sessionRef, Modifier.size(31.dp), PlayerMuted)
                    TouchControl("R", 11, sessionRef, Modifier.size(31.dp), PlayerMuted)
                    TouchControl("L2", 12, sessionRef, Modifier.size(31.dp), PlayerMuted, small = true)
                    TouchControl("R2", 13, sessionRef, Modifier.size(31.dp), PlayerMuted, small = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TouchControl("B", 0, sessionRef, Modifier.size(46.dp), PlayerCyan)
                    TouchControl("A", 8, sessionRef, Modifier.size(46.dp), PlayerCyan)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TouchControl("SELECT", 2, sessionRef, Modifier.height(29.dp).weight(1f), PlayerMuted, small = true)
                    TouchControl("START", 3, sessionRef, Modifier.height(29.dp).weight(1f), PlayerMuted, small = true)
                }
            }
        }
        Text("Touch • Bluetooth/USB • P2P em dois aparelhos", color = PlayerMuted, fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    if (showP2pDialog) {
        AlertDialog(
            onDismissRequest = { showP2pDialog = false },
            containerColor = PlayerPanel,
            title = { Text("JOGAR COM AMIGOS", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CONEXÃO DIRETA P2P", color = PlayerCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Abra o mesmo jogo nos dois aparelhos. Um jogador cria a sala e compartilha o código; o outro entra com esse código.", color = PlayerMuted, fontSize = 10.sp)
                    if (roomCode.isNotBlank()) {
                        Text("CÓDIGO DA SALA", color = PlayerMuted, fontSize = 9.sp)
                        Text(roomCode, color = PlayerCyan, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Vamos jogar no Retroboy Arcade! Abra o mesmo jogo e entre na sala com o código $roomCode.")
                                }
                                context.startActivity(Intent.createChooser(send, "Convidar jogador"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PlayerPurple)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.size(7.dp))
                            Text("COMPARTILHAR CÓDIGO")
                        }
                    }
                    Button(
                        onClick = {
                            val code = P2pRoomClient.createRoomCode()
                            startP2p(true, code)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PlayerCyan, contentColor = PlayerBg)
                    ) { Text("CRIAR SALA", fontWeight = FontWeight.Bold) }
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                        label = { Text("Código da sala") },
                        singleLine = true
                    )
                    Button(
                        onClick = { startP2p(false, joinCode) },
                        enabled = joinCode.length == 8,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PlayerPurple)
                    ) { Text("ENTRAR NA SALA") }
                    Text(p2pStatus, color = if (p2pStatus.contains("CONECTADO")) PlayerCyan else PlayerMuted, fontSize = 9.sp)
                    Text("O sinal é trocado pelo Supabase; os controles seguem diretamente entre os aparelhos por WebRTC. Se a rede usar CGNAT restritivo, será necessário configurar TURN.", color = PlayerMuted, fontSize = 8.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showP2pDialog = false }) { Text("FECHAR", color = PlayerCyan) } },
            dismissButton = {
                if (p2pClient != null) {
                    TextButton(onClick = {
                        p2pClient?.close()
                        p2pClient = null
                        sessionRef.value?.onLocalInputChanged = null
                        sessionRef.value?.setRemoteInputMask(0)
                        sessionRef.value?.setNetworkPlayerPort(0)
                        remoteMask = 0
                        roomCode = ""
                        p2pStatus = "OFFLINE"
                    }) { Text("SAIR DA SALA", color = Color(0xFFFF6B7A)) }
                }
            }
        )
    }
}

@Composable
private fun TouchControl(
    label: String,
    buttonId: Int,
    sessionRef: androidx.compose.runtime.MutableState<LibretroGameSession?>,
    modifier: Modifier = Modifier,
    tint: Color = PlayerPanel,
    small: Boolean = false
) {
    Box(
        modifier.clip(if (small) RoundedCornerShape(20.dp) else CircleShape)
            .background(tint.copy(alpha = .22f))
            .border(1.dp, tint.copy(alpha = .75f), if (small) RoundedCornerShape(20.dp) else CircleShape)
            .pointerInput(buttonId) {
                detectTapGestures(onPress = {
                    sessionRef.value?.setButton(buttonId, true)
                    tryAwaitRelease()
                    sessionRef.value?.setButton(buttonId, false)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = if (small) 8.sp else 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun md5File(file: File): String {
    val digest = MessageDigest.getInstance("MD5")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun safeStateFileName(title: String): String =
    title.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".state"
