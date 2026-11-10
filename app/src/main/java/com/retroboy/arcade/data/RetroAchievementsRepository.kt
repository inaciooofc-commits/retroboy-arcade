package com.retroboy.arcade.data

import android.content.Context
import com.retroboy.arcade.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class RetroGameAchievement(val id: Int, val title: String, val description: String, val points: Int, val badgeName: String)
data class RetroGameDetails(
    val gameId: Int, val title: String, val consoleName: String,
    val achievements: List<RetroGameAchievement>,
    val unlockedCount: Int, val totalCount: Int, val scoreAchieved: Int, val possibleScore: Int
)

class RetroAchievementsRepository(context: Context) {
    private val cacheDir = File(context.cacheDir, "retroachievements").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()

    suspend fun findGame(path: String, platform: Platform, username: String, apiKey: String): Result<RetroGameDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(username.isNotBlank()) { "Informe seu usuário RetroAchievements na aba Troféus." }
                require(apiKey.isNotBlank()) { "Informe sua Web API Key na aba Troféus." }
                val rom = File(path)
                require(rom.isFile) { "O arquivo do jogo não está disponível." }
                val hashes = collectHashes(rom)
                val candidates = consoleCandidates(rom.name, platform)
                val consoles = getArray("API_GetConsoleIDs.php", mapOf("y" to apiKey, "g" to "1", "a" to "1"))
                val console = (0 until consoles.length()).mapNotNull { consoles.optJSONObject(it) }
                    .firstOrNull { item -> candidates.any { normalize(item.optString("Name")) == normalize(it) } }
                    ?: throw IOException("Sistema não localizado no catálogo RetroAchievements.")
                val consoleId = console.optInt("ID")
                val games = getConsoleGames(apiKey, consoleId)
                var match: JSONObject? = null
                for (i in 0 until games.length()) {
                    val item = games.optJSONObject(i) ?: continue
                    val supported = item.optJSONArray("Hashes") ?: JSONArray()
                    for (j in 0 until supported.length()) {
                        if (hashes.contains(supported.optString(j).lowercase())) { match = item; break }
                    }
                    if (match != null) break
                }
                val gameId = match?.optInt("ID") ?: throw IOException(
                    "ROM não reconhecida pelo hash oficial. Tente importar a ROM extraída, não o arquivo 7z."
                )
                val extended = getObject("API_GetGameExtended.php", mapOf("y" to apiKey, "i" to gameId.toString(), "f" to "3"))
                val progressRoot = getObject("API_GetUserProgress.php", mapOf("y" to apiKey, "u" to username.trim(), "i" to gameId.toString()))
                val progress = progressRoot.optJSONObject(gameId.toString()) ?: JSONObject()
                val raw = extended.optJSONObject("Achievements") ?: JSONObject()
                val achievements = mutableListOf<RetroGameAchievement>()
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = raw.optJSONObject(key) ?: continue
                    achievements += RetroGameAchievement(
                        item.optInt("ID", key.toIntOrNull() ?: 0),
                        item.optString("Title", "Conquista"),
                        item.optString("Description"),
                        item.optInt("Points"),
                        item.optString("BadgeName")
                    )
                }
                RetroGameDetails(
                    gameId,
                    extended.optString("Title", match?.optString("Title") ?: rom.nameWithoutExtension),
                    extended.optString("ConsoleName", console.optString("Name")),
                    achievements.sortedBy { it.id },
                    progress.optInt("NumAchieved"),
                    progress.optInt("NumPossibleAchievements", achievements.size),
                    progress.optInt("ScoreAchieved"),
                    progress.optInt("PossibleScore")
                )
            }
        }

    private fun getConsoleGames(apiKey: String, consoleId: Int): JSONArray {
        val cache = File(cacheDir, "console-$consoleId.json")
        if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < 30L * 24 * 60 * 60 * 1000) {
            runCatching { return JSONArray(cache.readText()) }
        }
        val games = getArray("API_GetGameList.php", mapOf("y" to apiKey, "i" to consoleId.toString(), "h" to "1", "f" to "1"))
        runCatching { cache.writeText(games.toString()) }
        return games
    }

    private fun getArray(endpoint: String, params: Map<String, String>) = JSONArray(request(endpoint, params))
    private fun getObject(endpoint: String, params: Map<String, String>) = JSONObject(request(endpoint, params))
    private fun request(endpoint: String, params: Map<String, String>): String {
        val builder = HttpUrl.Builder().scheme("https").host("retroachievements.org").addPathSegments("API/$endpoint")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder().url(builder.build()).header("User-Agent", "RetroboyArcade/0.4").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("RetroAchievements respondeu HTTP ${response.code}.")
            if (body.isBlank() || body == "null") throw IOException("RetroAchievements não retornou dados para esta ROM.")
            return body
        }
    }

    private fun collectHashes(file: File): Set<String> {
        val result = mutableSetOf(md5(file))
        if (file.extension.equals("zip", true)) {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val digest = MessageDigest.getInstance("MD5")
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            digest.update(buffer, 0, count)
                        }
                        result += digest.digest().toHex()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return result
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun consoleCandidates(filename: String, platform: Platform): List<String> =
        when (filename.substringAfterLast('.', "").lowercase()) {
            "gb" -> listOf("Game Boy")
            "gbc" -> listOf("Game Boy Color")
            "gba", "bin" -> listOf("Game Boy Advance")
            "nes", "fds", "unif", "unf" -> listOf("NES/Famicom", "Nintendo Entertainment System")
            "sfc", "smc", "swc", "fig", "bs", "st" -> listOf("SNES/Super Famicom", "Super Nintendo Entertainment System")
            "md", "mdx", "smd", "gen", "sms", "gg", "sg" -> listOf("Mega Drive", "Genesis")
            "pbp", "chd", "iso", "cue", "ccd", "toc", "m3u", "img" ->
                if (platform == Platform.PC_ENGINE) listOf("PC Engine/TurboGrafx-16", "PC Engine") else listOf("PlayStation")
            "a26" -> listOf("Atari 2600")
            "lnx" -> listOf("Atari Lynx")
            "pce" -> listOf("PC Engine/TurboGrafx-16", "PC Engine")
            "zip", "7z" -> if (platform == Platform.NEOGEO) listOf("Neo Geo") else listOf("Arcade")
            else -> listOf(platform.label)
        }
}
