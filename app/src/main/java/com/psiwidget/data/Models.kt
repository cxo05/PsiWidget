package com.psiwidget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true }

enum class Region(val key: String, val label: String) {
    NORTH("north", "North"),
    SOUTH("south", "South"),
    EAST("east", "East"),
    WEST("west", "West"),
    CENTRAL("central", "Central");

    companion object {
        fun fromKey(key: String?): Region? = entries.firstOrNull { it.key == key }
    }
}

enum class Metric(val key: String, val label: String, val unit: String?) {
    PSI("psi", "PSI", null),
    PM25("pm25", "PM2.5", "µg/m³"),
    PM10("pm10", "PM10", "µg/m³");

    companion object {
        fun fromKey(key: String?): Metric = entries.firstOrNull { it.key == key } ?: PSI
    }
}

/** NEA PSI bands. PM2.5 and PM10 are banded by their sub-index, which uses the same scale. */
enum class Band(val label: String, val max: Int) {
    GOOD("Good", 50),
    MODERATE("Moderate", 100),
    UNHEALTHY("Unhealthy", 200),
    VERY_UNHEALTHY("Very unhealthy", 300),
    HAZARDOUS("Hazardous", Int.MAX_VALUE);

    companion object {
        fun of(index: Int): Band = entries.first { index <= it.max }
    }
}

@Serializable
data class LatLng(val latitude: Double, val longitude: Double)

/** One reading from the API, keyed by region key ("north", "south", ...). */
@Serializable
data class PsiSnapshot(
    val updated: String,
    val psi: Map<String, Int>,
    val pm25: Map<String, Int>,
    val pm10: Map<String, Int>,
    val pm25Sub: Map<String, Int>,
    val pm10Sub: Map<String, Int>,
    val regionLocations: Map<String, LatLng> = emptyMap(),
) {
    fun value(metric: Metric, region: Region): Int? = values(metric)[region.key]

    /** The index used for colouring, falling back to the raw value if no sub-index was sent. */
    fun band(metric: Metric, region: Region): Band? {
        val index = when (metric) {
            Metric.PSI -> psi
            Metric.PM25 -> pm25Sub
            Metric.PM10 -> pm10Sub
        }[region.key] ?: value(metric, region)
        return index?.let(Band::of)
    }

    fun highest(metric: Metric): Region? =
        Region.entries.filter { value(metric, it) != null }.maxByOrNull { value(metric, it)!! }

    private fun values(metric: Metric) = when (metric) {
        Metric.PSI -> psi
        Metric.PM25 -> pm25
        Metric.PM10 -> pm10
    }
}
