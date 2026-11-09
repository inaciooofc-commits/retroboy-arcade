package com.retroboy.arcade.data

import com.retroboy.arcade.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class DeveloperAccessRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun hasDeveloperAccess(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() ||
            !BuildConfig.SUPABASE_URL.startsWith("https://") ||
            BuildConfig.SUPABASE_ANON_KEY.isBlank()
        ) return@withContext false

        runCatching {
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/user_roles?select=role&limit=5")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    false
                } else {
                    val rows = JSONArray(response.body?.string().orEmpty())
                    (0 until rows.length()).any { index ->
                        rows.getJSONObject(index).optString("role") == "developer" ||
                            rows.getJSONObject(index).optString("role") == "admin"
                    }
                }
            }
        }.getOrDefault(false)
    }
}
