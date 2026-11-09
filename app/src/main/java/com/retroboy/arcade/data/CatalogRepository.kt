package com.retroboy.arcade.data

import com.retroboy.arcade.BuildConfig
import com.retroboy.arcade.model.CatalogAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CatalogRepository {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun fetchAssets(): Result<List<CatalogAsset>> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.CATALOG_URL.startsWith("https://")) { "Configure RETROBOY_CATALOG_URL com o endereço HTTPS do catálogo público." }
            val request = Request.Builder().url(BuildConfig.CATALOG_URL).get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Catálogo indisponível (${response.code})." }
                val root = JSONObject(response.body?.string().orEmpty())
                val array = root.optJSONArray("assets") ?: return@use emptyList<CatalogAsset>()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(CatalogAsset(
                            id = item.optString("id"), title = item.optString("title"),
                            description = item.optString("description"), kind = item.optString("kind"),
                            version = item.optString("version"), url = item.optString("url"),
                            sha256 = item.optString("sha256"), sizeBytes = item.optLong("sizeBytes"),
                            distribution = item.optString("distribution")
                        ))
                    }
                }
            }
        }
    }
}
