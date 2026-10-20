package com.psiwidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object PsiApi {
    private const val PSI_ENDPOINT = "https://api-open.data.gov.sg/v2/real-time/api/psi"
    private const val PM25_ENDPOINT = "https://api-open.data.gov.sg/v2/real-time/api/pm25"

    /** PSI is required; hourly PM2.5 is best effort so its outage doesn't take the whole widget offline. */
    suspend fun fetch(): PsiSnapshot = coroutineScope {
        val hourly = async { runCatching { parseHourlyPm25(get(PM25_ENDPOINT)) }.getOrNull() }
        val psi = parse(get(PSI_ENDPOINT))
        hourly.await()?.let { (updated, values) -> psi.copy(pm25Hourly = values, pm25HourlyUpdated = updated) } ?: psi
    }

    private suspend fun get(endpoint: String): String = withContext(Dispatchers.IO) {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(body: String): PsiSnapshot {
        val (data, item) = firstItem(body)
        fun reading(key: String) = item.reading(key)

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

    /** Returns the publish time and the one-hour PM2.5 reading per region. */
    fun parseHourlyPm25(body: String): Pair<String, Map<String, Int>> {
        val (_, item) = firstItem(body)
        val values = item.reading("pm25_one_hourly")
        if (values.isEmpty()) throw IOException("Response has no pm25_one_hourly")
        return (item.updatedTimestamp ?: item.timestamp.orEmpty()) to values
    }

    private fun firstItem(body: String): Pair<Data, Item> {
        val response = AppJson.decodeFromString<Response>(body)
        val data = response.data
        val item = data?.items?.firstOrNull()
            ?: throw IOException("No readings (${response.errorMsg ?: "code ${response.code}"})")
        return data to item
    }

    private fun Item.reading(key: String) = readings[key].orEmpty().mapValues { it.value.roundToInt() }

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
