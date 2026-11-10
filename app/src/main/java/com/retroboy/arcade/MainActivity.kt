package com.retroboy.arcade

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroboy.arcade.data.AchievementCatalog
import com.retroboy.arcade.data.AuthRepository
import com.retroboy.arcade.data.CatalogRepository
import com.retroboy.arcade.data.DeveloperAccessRepository
import com.retroboy.arcade.emulation.CoreCatalog
import com.retroboy.arcade.emulation.CoreInstaller
import com.retroboy.arcade.model.Achievement
import com.retroboy.arcade.model.GameEntry
import com.retroboy.arcade.model.Platform
import kotlinx.coroutines.launch

private val Night = Color(0xFF070918)
private val Panel = Color(0xFF111631)
private val Panel2 = Color(0xFF171F3B)
private val Purple = Color(0xFF8F5BFF)
private val Cyan = Color(0xFF00EFFF)
private val Pink = Color(0xFFFF2DD6)
private val Muted = Color(0xFF93A4C8)
private val BrandFont = FontFamily(Font(R.font.stranger_title))
private val DisplayFont = BrandFont

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroboyApp() }
    }
}

@Composable
private fun RetroboyApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("retroboy-local", 0) }
    var email by remember { mutableStateOf("") }
    var guest by remember { mutableStateOf(prefs.getBoolean("guest", false)) }
    var tab by remember { mutableStateOf("Início") }
    var authVisible by remember { mutableStateOf(email.isBlank() && !guest) }
    var createAccount by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<GameEntry?>(null) }
    var importedGames by remember { mutableStateOf(loadImportedGames(prefs)) }
    var authMessage by remember { mutableStateOf("") }
    val authRepository = remember { AuthRepository() }
    val catalogRepository = remember { CatalogRepository() }
    val developerAccessRepository = remember { DeveloperAccessRepository() }
    val coreInstaller = remember { CoreInstaller(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var isDeveloper by remember { mutableStateOf(false) }
    var installedCoreIds by remember { mutableStateOf(coreInstaller.installedCoreIds()) }
    var installingCore by remember { mutableStateOf<String?>(null) }
    var coreInstallMessage by remember { mutableStateOf("") }
    var catalogCount by remember { mutableIntStateOf(0) }
    var catalogMessage by remember { mutableStateOf("Catálogo ainda não consultado") }
    var assetsLoading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = queryDisplayName(context, uri)
            val entry = GameEntry("local-${uri.toString().hashCode()}", name, Platform.ARCADE, "Arquivo local importado", 0xFF45D9E8, uri.toString())
            importedGames = (importedGames.filterNot { it.importedUri == uri.toString() } + entry)
            saveImportedGames(prefs, importedGames)
            authMessage = "Arquivo adicionado à biblioteca. O núcleo de emulação ainda precisa ser integrado."
            tab = "Jogos"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, secondary = Cyan, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171027), Night, Color(0xFF08070E))))) {
            if (authVisible) {
                AuthScreen(
                    createAccount = createAccount,
                    onToggleMode = { createAccount = !createAccount; authMessage = "" },
                    onGuest = { guest = true; isDeveloper = false; prefs.edit().putBoolean("guest", true).apply(); authVisible = false },
                    message = authMessage,
                    onSubmit = { inputEmail, password ->
                        authMessage = "Conectando ao Supabase…"
                        scope.launch {
                            val result = if (createAccount) authRepository.signUp(inputEmail, password) else authRepository.signIn(inputEmail, password)
                            result.onSuccess { user ->
                                if (createAccount && user.accessToken.isNullOrBlank()) {
                                    createAccount = false
                                    authMessage = "Conta criada. Confirme seu e-mail e depois entre com sua senha."
                                } else {
                                    email = user.email
                                    isDeveloper = user.accessToken?.let { developerAccessRepository.hasDeveloperAccess(it) } ?: false
                                    guest = false
                                    prefs.edit().remove("email").putBoolean("guest", false).apply()
                                    authVisible = false
                                    authMessage = ""
                                }
                            }.onFailure { authMessage = it.message ?: "Não foi possível autenticar." }
                        }
                    }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header(email = email, guest = guest, isDeveloper = isDeveloper)
                    AnimatedContent(targetState = tab, label = "main-tab", modifier = Modifier.weight(1f)) { current ->
                        when (current) {
                            "Início" -> HomeScreen(
                                email = email, importedCount = importedGames.size,
                                onOpenGames = { tab = "Jogos" }, onOpenDownloads = { tab = "Downloads" },
                                onOpenAchievements = { tab = "Conquistas" }, onOpenAuth = { authVisible = true }
                            )
                            "Jogos" -> LibraryScreen(importedGames, onImport = { picker.launch(arrayOf("*/*")) }, onSelect = { selectedGame = it })
                            "Conquistas" -> AchievementsScreen(AchievementCatalog.all)
                            "Downloads" -> DownloadsScreen(
                                count = catalogCount, message = catalogMessage, loading = assetsLoading,
                                installedCoreIds = installedCoreIds, installingCore = installingCore,
                                coreInstallMessage = coreInstallMessage,
                                onInstallCore = { coreId ->
                                    if (installingCore == null && !installedCoreIds.contains(coreId)) {
                                        installingCore = coreId
                                        coreInstallMessage = "Baixando núcleo oficial do Libretro…"
                                        scope.launch {
                                            coreInstaller.install(coreId).onSuccess { installed ->
                                                installedCoreIds = coreInstaller.installedCoreIds()
                                                coreInstallMessage = installed.title + " instalado e validado (" + installed.sha256.take(12) + "). O runtime de execução ainda está em integração."
                                            }.onFailure { error ->
                                                coreInstallMessage = error.message ?: "Falha ao instalar o núcleo."
                                            }
                                            installingCore = null
                                        }
                                    }
                                },
                                onRefresh = {
                                    assetsLoading = true
                                    scope.launch {
                                        catalogRepository.fetchAssets().onSuccess { assets ->
                                            catalogCount = assets.size
                                            catalogMessage = if (assets.isEmpty()) "Catálogo conectado, sem arquivos publicados ainda." else "${assets.size} itens encontrados"
                                        }.onFailure { catalogMessage = it.message ?: "Falha ao consultar catálogo." }
                                        assetsLoading = false
                                    }
                                }
                            )
                            else -> ProfileScreen(email, guest, isDeveloper, onLogin = { authVisible = true }, onLogout = {
                                email = ""; guest = false; isDeveloper = false; prefs.edit().clear().apply(); authVisible = true
                            })
                        }
                    }
                    BottomBar(tab = tab, onSelect = { tab = it })
                }
            }
            selectedGame?.let { game ->
                GameInfoDialog(game = game, onDismiss = { selectedGame = null })
            }
        }
    }
}

@Composable
private fun Header(email: String, guest: Boolean, isDeveloper: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Purple, Pink))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("RETROBOY", color = Color.White, fontFamily = BrandFont, fontSize = 27.sp, letterSpacing = 1.2.sp)
            Text("ARCADE", color = Cyan, fontFamily = DisplayFont, fontSize = 13.sp, letterSpacing = 2.4.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (isDeveloper) "DEVELOPER" else if (guest) "GUEST PLAYER" else "PLAYER", color = if (isDeveloper) Color(0xFFFFDE4D) else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(if (guest) "Offline profile" else email.substringBefore('@'), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeScreen(email: String, importedCount: Int, onOpenGames: () -> Unit, onOpenDownloads: () -> Unit, onOpenAchievements: () -> Unit, onOpenAuth: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF38205F), Color(0xFF171326), Color(0xFF102C3A)))).border(1.dp, Color(0x665E4D8A), RoundedCornerShape(24.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("INSERT COIN TO CONTINUE", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("SEU FLIPERAMA.\nSUAS REGRAS.", color = Color.White, fontFamily = DisplayFont, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = .5.sp)
                Text("Clássicos, desafios e partidas com seus amigos.", color = Color(0xFFD2C9E2), fontSize = 13.sp)
                Button(onClick = onOpenGames, colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("ABRIR BIBLIOTECA", fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("JOGOS LOCAIS", importedCount.toString(), Icons.Rounded.VideogameAsset, Modifier.weight(1f))
            StatCard("CONQUISTAS", "0 / ${AchievementCatalog.all.size}", Icons.Rounded.EmojiEvents, Modifier.weight(1f))
            StatCard("AMIGOS", "0", Icons.Rounded.Groups, Modifier.weight(1f))
        }
        SectionTitle("SISTEMAS", "EXPLORE A COLEÇÃO")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemCard("NEO GEO", "Luta • Ação", Purple, Modifier.weight(1f))
            SystemCard("ARCADE", "Fliperamas", Cyan, Modifier.weight(1f))
            SystemCard("GBA", "Aventura", Pink, Modifier.weight(1f))
        }
        SectionTitle("CENTRAL DO JOGADOR", "SEU PROGRESSO")
        ActionCard(Icons.Rounded.EmojiEvents, "Conquistas", "Desbloqueie títulos e pontos jogando.", onOpenAchievements)
        ActionCard(Icons.Rounded.CloudDownload, "Central de downloads", "Catálogo de configurações e conteúdo autorizado.", onOpenDownloads)
        if (email.isBlank()) ActionCard(Icons.Rounded.PersonAdd, "Conecte sua conta", "Sincronização de perfil será ativada após configurar o Supabase.", onOpenAuth)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Panel).padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(18.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = .6.sp, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontFamily = DisplayFont, fontSize = 18.sp, letterSpacing = .8.sp)
        Text(subtitle, color = Cyan.copy(alpha = .8f), fontFamily = DisplayFont, fontSize = 10.sp, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun SystemCard(title: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Panel2).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(15.dp)).padding(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SportsEsports, null, tint = color, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(sub, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Purple.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Purple) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(description, color = Muted, fontSize = 10.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun LibraryScreen(games: List<GameEntry>, onImport: () -> Unit, onSelect: (GameEntry) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SectionTitle("BIBLIOTECA", "SEUS JOGOS NO APARELHO")
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(13.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("IMPORTAR JOGO DO CELULAR")
        }
        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SportsEsports, null, tint = Purple, modifier = Modifier.size(50.dp))
                    Text("Sua biblioteca está vazia", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Importe um arquivo de jogo compatível para começar.", color = Muted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(games, key = { it.id }) { game ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onSelect(game) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(game.accent).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.VideogameAsset, null, tint = Color(game.accent))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(game.subtitle, color = Muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsScreen(achievements: List<Achievement>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("CONQUISTAS", "CADA FICHA CONTA")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel2).padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("SEU PLACAR", color = Muted, fontSize = 9.sp); Text("0 PTS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
            Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFFFD166), modifier = Modifier.size(38.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(achievements, key = { it.id }) { achievement -> AchievementRow(achievement) }
        }
    }
}

@Composable
private fun AchievementRow(item: Achievement) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (item.unlocked) Color(0xFFFFD166).copy(alpha = .17f) else Panel2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = if (item.unlocked) Color(0xFFFFD166) else Muted)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(item.description, color = Muted, fontSize = 10.sp)
            LinearProgressIndicator(progress = { (item.progress.toFloat() / item.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Cyan, trackColor = Panel2)
        }
        Spacer(Modifier.width(8.dp))
        Text("${item.points} XP", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun DownloadsScreen(
    count: Int,
    message: String,
    loading: Boolean,
    installedCoreIds: Set<String>,
    installingCore: String?,
    coreInstallMessage: String,
    onInstallCore: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("DOWNLOAD CENTER", "CATÁLOGO RETROBOY")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF352050), Color(0xFF152A38)))).padding(17.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CloudDownload, null, tint = Cyan, modifier = Modifier.size(30.dp))
                Text("Conteúdo e configurações", color = Color.White, fontFamily = DisplayFont, fontSize = 19.sp)
                Text("Capas, configurações e conteúdo autorizado. ROMs comerciais e BIOS protegidas devem ser fornecidas pelo próprio usuário.", color = Color(0xFFD0C7DE), fontSize = 11.sp)
            }
        }
        Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Sync, null)
            Spacer(Modifier.width(8.dp)); Text(if (loading) "CONSULTANDO…" else "CONSULTAR CATÁLOGO GITHUB", fontFamily = DisplayFont)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Inventory2, null, tint = Cyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Itens publicados", color = Color.White, fontFamily = DisplayFont, fontSize = 16.sp)
                Text(message, color = Muted, fontSize = 10.sp)
            }
            Text(count.toString(), color = Cyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        SectionTitle("MOTORES DE EMULAÇÃO", "DOWNLOAD DIRETO DO BUILD BOT OFICIAL LIBRETRO")
        CoreCatalog.all.forEach { core ->
            val installed = installedCoreIds.contains(core.id)
            val busy = installingCore == core.id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).border(1.dp, if (installed) Color(0xFF4AFFA3).copy(alpha = .45f) else Purple.copy(alpha = .3f), RoundedCornerShape(15.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Panel2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Memory, null, tint = if (installed) Color(0xFF4AFFA3) else Cyan, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(core.title, color = Color.White, fontFamily = DisplayFont, fontSize = 16.sp)
                    Text(core.systems, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(core.description, color = Muted, fontSize = 9.sp)
                    Text("Licença: " + core.license + if (core.netplay) " • Netplay suportado" else " • Uso local", color = Muted, fontSize = 8.sp)
                }
                Spacer(Modifier.width(6.dp))
                if (installed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4AFFA3), modifier = Modifier.size(22.dp))
                        Text("OK", color = Color(0xFF4AFFA3), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onInstallCore(core.id) },
                        enabled = installingCore == null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("BAIXAR", fontFamily = DisplayFont, fontSize = 10.sp)
                    }
                }
            }
        }
        if (coreInstallMessage.isNotBlank()) {
            Text(coreInstallMessage, color = if (coreInstallMessage.contains("instalado")) Color(0xFF4AFFA3) else Cyan, fontSize = 10.sp)
        }
        Text("A instalação verifica o arquivo nativo e a ABI do aparelho. O runtime que executa os jogos e o netplay integrado ainda precisam ser concluídos.", color = Muted, fontSize = 10.sp)
        Text("O catálogo será ativado quando o manifesto GitHub estiver publicado.", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileScreen(email: String, guest: Boolean, isDeveloper: Boolean, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("JOGADOR", "SEU PERFIL")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel2).padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Pink))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                Text(if (guest) "Jogador convidado" else email.substringBefore('@'), color = Color.White, fontFamily = DisplayFont, fontSize = 20.sp)
                Text(if (guest) "Progresso salvo neste aparelho" else email, color = Muted, fontSize = 11.sp)
                if (isDeveloper) {
                    Text("DEVELOPER ACCESS • AUTORIZADO PELO SUPABASE", color = Color(0xFFFFDE4D), fontFamily = DisplayFont, fontSize = 10.sp, letterSpacing = .6.sp)
                }
            }
        }
        ActionCard(Icons.Rounded.Security, "Conta e segurança", "Autenticação por e-mail e senha com Supabase.", onLogin)
        ActionCard(Icons.Rounded.Logout, if (guest) "Entrar em uma conta" else "Sair da conta", "Gerencie a sessão neste dispositivo.", onLogout)
        Text("Retroboy Arcade • versão 0.2.0-alpha", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun AuthScreen(createAccount: Boolean, onToggleMode: () -> Unit, onGuest: () -> Unit, message: String, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Purple, Pink))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(17.dp))
        Text("RETROBOY", color = Color.White, fontFamily = BrandFont, fontSize = 34.sp, letterSpacing = 1.8.sp)
        Text("ARCADE  •  PLAY THE CLASSICS", color = Cyan, fontFamily = DisplayFont, fontSize = 14.sp, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(30.dp))
        Text(if (createAccount) "CRIAR CONTA" else "BEM-VINDO DE VOLTA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = authFieldColors())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Senha (mínimo 6 caracteres)") }, singleLine = true, shape = RoundedCornerShape(13.dp), colors = authFieldColors())
        Spacer(Modifier.height(13.dp))
        Button(onClick = { onSubmit(email, password) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(13.dp)) { Text(if (createAccount) "CRIAR MINHA CONTA" else "ENTRAR", fontWeight = FontWeight.Black) }
        TextButton(onClick = onToggleMode) { Text(if (createAccount) "Já tenho uma conta • Entrar" else "Ainda não tenho conta • Cadastrar", color = Cyan) }
        OutlinedButton(onClick = onGuest, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("CONTINUAR COMO CONVIDADO") }
        if (message.isNotBlank()) Text(message, color = if (message.contains("Conectando") || message.contains("Conta criada")) Cyan else Color(0xFFFFB4AB), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(20.dp))
        Text("O modo convidado funciona sem cadastro. A sincronização em nuvem exige configurar o projeto Supabase.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = Purple, unfocusedBorderColor = Color(0xFF514765),
    focusedLabelColor = Cyan, unfocusedLabelColor = Muted,
    cursorColor = Cyan
)

@Composable
private fun BottomBar(tab: String, onSelect: (String) -> Unit) {
    val items = listOf("Início" to Icons.Rounded.Home, "Jogos" to Icons.Rounded.SportsEsports, "Conquistas" to Icons.Rounded.EmojiEvents, "Downloads" to Icons.Rounded.CloudDownload, "Perfil" to Icons.Rounded.Person)
    Row(Modifier.fillMaxWidth().background(Color(0xFF100D1A)).padding(horizontal = 5.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEach { (label, icon) ->
            val selected = tab == label
            Column(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).clickable { onSelect(label) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, null, tint = if (selected) Cyan else Muted, modifier = Modifier.size(20.dp))
                Text(label, color = if (selected) Color.White else Muted, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun GameInfoDialog(game: GameEntry, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel2,
        title = { Text(game.title, color = Color.White, fontWeight = FontWeight.Black) },
        text = { Text("Arquivo reconhecido na biblioteca. A execução real depende da integração e distribuição dos núcleos de emulação compatíveis. Esta versão inicial ainda não executa ROMs.", color = Muted) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ENTENDI", color = Cyan) } })
}

private fun loadImportedGames(prefs: android.content.SharedPreferences): List<GameEntry> =
    prefs.getStringSet("imported_uris", emptySet()).orEmpty().map { raw ->
        GameEntry("local-${raw.hashCode()}", raw.substringAfterLast('/').substringAfterLast(':').ifBlank { "Jogo importado" }, Platform.ARCADE, "Arquivo local importado", 0xFF45D9E8, raw)
    }

private fun saveImportedGames(prefs: android.content.SharedPreferences, games: List<GameEntry>) {
    prefs.edit().putStringSet("imported_uris", games.mapNotNull { it.importedUri }.toSet()).apply()
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        // Use o nome apresentado pelo provedor de documentos, sem depender do caminho interno do URI.
        // Se o provedor não informar o nome, use um rótulo neutro.
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }?.takeIf { it.isNotBlank() } ?: "Jogo importado"
    }.getOrDefault("Jogo importado")
}
