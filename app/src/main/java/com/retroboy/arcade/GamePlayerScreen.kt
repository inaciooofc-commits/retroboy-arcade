package com.retroboy.arcade

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowLeft
import androidx.compose.material.icons.rounded.ArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retroboy.arcade.emulation.LibretroGameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PlayerBg = Color(0xFF070918)
private val PlayerPanel = Color(0xFF111631)
private val PlayerCyan = Color(0xFF00EFFF)
private val PlayerPurple = Color(0xFF8F5BFF)
private val PlayerMuted = Color(0xFF93A4C8)

@Composable
fun GamePlayerScreen(
    title: String,
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
    val gameExtension = gamePath.substringAfterLast('.', "").lowercase()
    val gameAspectRatio = when (gameExtension) {
        "gba" -> 3f / 2f
        "gb", "gbc" -> 10f / 9f
        else -> 4f / 3f
    }
    val stateFile = remember(gamePath, saveDirectory) { File(saveDirectory, safeStateFileName(File(gamePath).nameWithoutExtension)) }
    var hasSaveState by remember { mutableStateOf(stateFile.isFile) }

    BackHandler(enabled = true) {
        sessionRef.value?.close()
        sessionRef.value = null
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            sessionRef.value?.close()
            sessionRef.value = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(PlayerBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                sessionRef.value?.close()
                sessionRef.value = null
                onClose()
            }) {
                Icon(Icons.Rounded.Close, contentDescription = "Fechar jogo", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(status, color = if (status.startsWith("ERRO")) Color(0xFFFF6B7A) else PlayerCyan, fontSize = 9.sp)
            }
            IconButton(onClick = {
                paused = !paused
                sessionRef.value?.pause(paused)
            }) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (paused) "Retomar" else "Pausar",
                    tint = PlayerCyan)
            }
            IconButton(onClick = { sessionRef.value?.reset() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar jogo", tint = PlayerCyan)
            }
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
                TouchControl("▲", 4, sessionRef, Modifier.size(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TouchControl("◀", 6, sessionRef, Modifier.size(48.dp))
                    TouchControl("▼", 5, sessionRef, Modifier.size(48.dp))
                    TouchControl("▶", 7, sessionRef, Modifier.size(48.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchControl("Y", 1, sessionRef, Modifier.size(44.dp), PlayerPurple)
                    TouchControl("X", 9, sessionRef, Modifier.size(44.dp), PlayerPurple)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchControl("L", 10, sessionRef, Modifier.size(36.dp), PlayerMuted)
                    TouchControl("R", 11, sessionRef, Modifier.size(36.dp), PlayerMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchControl("B", 0, sessionRef, Modifier.size(48.dp), PlayerCyan)
                    TouchControl("A", 8, sessionRef, Modifier.size(48.dp), PlayerCyan)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TouchControl("SELECT", 2, sessionRef, Modifier.height(30.dp).weight(1f), PlayerMuted, small = true)
                    TouchControl("START", 3, sessionRef, Modifier.height(30.dp).weight(1f), PlayerMuted, small = true)
                }
            }
        }
        Text("Controles touch • gamepad Bluetooth/USB", color = PlayerMuted, fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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

private fun safeStateFileName(title: String): String =
    title.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".state"
