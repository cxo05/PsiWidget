package com.psiwidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object PsiApi {
    private const val ENDPOINT = "https://api-open.data.gov.sg/v2/real-time/api/psi"

    suspend fun fetch(): PsiSnapshot = withContext(Dispatchers.IO) {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    fun parse(body: String): PsiSnapshot {
        val response = AppJson.decodeFromString<Response>(body)
        val data = response.data
        val item = data?.items?.firstOrNull()
            ?: throw IOException("No PSI readings (${response.errorMsg ?: "code ${response.code}"})")

        fun reading(key: String) = item.readings[key].orEmpty().mapValues { it.value.roundToInt() }

        val psi = reading("psi_twenty_four_hourly")
        if (psi.isEmpty()) throw IOException("Response has no psi_twenty_four_hourly")

        return PsiSnapshot(
            updated = item.updatedTimestamp ?: item.timestamp.orEmpty(),
            psi = psi,
            pm25 = reading("pm25_twenty_four_hourly"),
            pm10 = reading("pm10_twenty_four_hourly"),
            pm25Sub = reading("pm25_sub_index"),
            pm10Sub = reading("pm10_sub_index"),
            regionLocations = data.regionMetadata.associate { it.name to it.labelLocation },
        )
    }

    @Serializable
    private data class Response(val code: Int = 0, val data: Data? = null, val errorMsg: String? = null)

    @Serializable
    private data class Data(
        val regionMetadata: List<RegionMeta> = emptyList(),
        val items: List<Item> = emptyList(),
    )

    @Serializable
    private data class RegionMeta(val name: String, val labelLocation: LatLng)

    @Serializable
    private data class Item(
        val updatedTimestamp: String? = null,
        val timestamp: String? = null,
        val readings: Map<String, Map<String, Double>> = emptyMap(),
    )
}
