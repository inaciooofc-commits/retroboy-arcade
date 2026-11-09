package com.retroboy.arcade.data

import com.retroboy.arcade.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthUser(val email: String, val accessToken: String? = null)

class AuthRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun signUp(email: String, password: String): Result<AuthUser> = request("/auth/v1/signup", email, password)

    suspend fun signIn(email: String, password: String): Result<AuthUser> =
        request("/auth/v1/token?grant_type=password", email, password)

    private suspend fun request(path: String, email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured) { "Supabase ainda não foi configurado. Adicione SUPABASE_URL e SUPABASE_ANON_KEY." }
            require(email.contains("@")) { "Informe um e-mail válido." }
            require(password.length >= 6) { "A senha precisa ter pelo menos 6 caracteres." }
            val json = JSONObject().put("email", email.trim()).put("password", password)
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(responseText).optString("msg").ifBlank { JSONObject(responseText).optString("message").ifBlank { JSONObject(responseText).optString("error_description") } } }.getOrNull()
                    throw IOException(message?.takeIf { it.isNotBlank() } ?: "Falha na autenticação (${response.code}).")
                }
                val payload = JSONObject(responseText)
                val user = payload.optJSONObject("user")
                val returnedEmail = user?.optString("email")?.takeIf { it.isNotBlank() } ?: email
                AuthUser(returnedEmail, payload.optString("access_token").takeIf { it.isNotBlank() })
            }
        }
    }
}
