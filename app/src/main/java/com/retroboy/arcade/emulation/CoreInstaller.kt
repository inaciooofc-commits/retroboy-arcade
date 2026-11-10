package com.retroboy.arcade.emulation

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class InstalledCore(
    val id: String,
    val title: String,
    val abi: String,
    val libraryPath: String,
    val sha256: String,
    val probe: String
)

class CoreInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    private fun selectedAbi(): String? {
        return Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" }
    }

    private fun coreFile(coreId: String, abi: String): File {
        return File(appContext.filesDir, "libretro/cores/" + abi + "/" + coreId + "_libretro_android.so")
    }

    fun installedCoreIds(): Set<String> {
        val abi = selectedAbi() ?: return emptySet()
        return CoreCatalog.all.filter { coreFile(it.id, abi).isFile }.map { it.id }.toSet()
    }

    fun installedCorePath(coreId: String): String? {
        val abi = selectedAbi() ?: return null
        return coreFile(coreId, abi).takeIf { it.isFile }?.absolutePath
    }

    suspend fun install(coreId: String): Result<InstalledCore> = withContext(Dispatchers.IO) {
        runCatching {
            val core = CoreCatalog.find(coreId) ?: throw IOException("Núcleo desconhecido.")
            val abi = selectedAbi() ?: throw IOException("Este aparelho não possui uma ABI Android compatível.")
            val target = coreFile(core.id, abi)
            val directory = target.parentFile ?: throw IOException("Não foi possível preparar a pasta de núcleos.")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Não foi possível criar a pasta de instalação.")
            }

            val temporary = File(directory, core.id + ".download.tmp")
            if (temporary.exists()) temporary.delete()
            val request = Request.Builder().url(CoreCatalog.downloadUrl(core.id, abi)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Buildbot Libretro respondeu HTTP " + response.code + ".")
                }
                val body = response.body ?: throw IOException("O pacote do núcleo veio vazio.")
                var extracted = false
                ZipInputStream(BufferedInputStream(body.byteStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val filename = entry.name.substringAfterLast('/')
                        if (!entry.isDirectory && filename.endsWith(".so") && filename.startsWith(core.id)) {
                            FileOutputStream(temporary).use { output -> zip.copyTo(output) }
                            extracted = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                if (!extracted) throw IOException("O pacote não contém a biblioteca esperada para " + core.title + ".")
            }

            if (!temporary.isFile || temporary.length() < 1024L) {
                temporary.delete()
                throw IOException("A biblioteca baixada está incompleta.")
            }

            val probe = try {
                NativeCoreProbe.probeCore(temporary.absolutePath)
            } catch (error: Throwable) {
                temporary.delete()
                throw IOException("Não foi possível carregar a biblioteca nativa: " + (error.message ?: "erro desconhecido"))
            }
            if (!probe.startsWith("OK")) {
                temporary.delete()
                throw IOException("A biblioteca não passou na verificação Libretro: " + probe)
            }

            val backup = File(directory, core.id + ".previous")
            if (backup.exists()) backup.delete()
            if (target.exists() && !target.renameTo(backup)) {
                temporary.delete()
                throw IOException("Não foi possível preservar a instalação anterior.")
            }
            if (!temporary.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                temporary.delete()
                throw IOException("Não foi possível finalizar a instalação do núcleo.")
            }
            if (backup.exists()) backup.delete()

            InstalledCore(
                id = core.id,
                title = core.title,
                abi = abi,
                libraryPath = target.absolutePath,
                sha256 = sha256(target),
                probe = probe
            )
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
