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

enum class Metric(val key: String, val label: String, val unit: String?, val window: String) {
    PSI("psi", "PSI", null, "24h"),
    PM25("pm25", "PM2.5", "µg/m³", "24h"),
    PM10("pm10", "PM10", "µg/m³", "24h"),
    PM25_1H("pm25_1h", "PM2.5 1h", "µg/m³", "1h");

    /** Hourly PM2.5 uses NEA's 1-hour bands (Normal, Elevated, ...), not the PSI descriptors. */
    fun bandLabel(band: Band): String = if (this == PM25_1H) band.hourlyLabel else band.label

    companion object {
        fun fromKey(key: String?): Metric = entries.firstOrNull { it.key == key } ?: PSI
    }
}

/**
 * NEA PSI bands. PM2.5 and PM10 are banded by their sub-index, which uses the same scale.
 * Hourly PM2.5 has its own four bands (Normal to Very high), which borrow the first four colours.
 */
enum class Band(val label: String, val max: Int, val hourlyLabel: String, val hourlyMax: Int) {
    GOOD("Good", 50, "Normal", 55),
    MODERATE("Moderate", 100, "Elevated", 150),
    UNHEALTHY("Unhealthy", 200, "High", 250),
    VERY_UNHEALTHY("Very unhealthy", 300, "Very high", Int.MAX_VALUE),
    HAZARDOUS("Hazardous", Int.MAX_VALUE, "Very high", Int.MAX_VALUE);

    companion object {
        fun of(index: Int): Band = entries.first { index <= it.max }

        /** Bands a one-hour PM2.5 concentration in µg/m³. */
        fun ofHourlyPm25(value: Int): Band = entries.first { value <= it.hourlyMax }
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
    /** One-hour PM2.5 from a separate endpoint; empty if that fetch failed. */
    val pm25Hourly: Map<String, Int> = emptyMap(),
    val pm25HourlyUpdated: String? = null,
) {
    /** When the readings for [metric] were last published. */
    fun updated(metric: Metric): String? = if (metric == Metric.PM25_1H) pm25HourlyUpdated else updated

    fun value(metric: Metric, region: Region): Int? = values(metric)[region.key]

    /** The index used for colouring, falling back to the raw value if no sub-index was sent. */
    fun band(metric: Metric, region: Region): Band? {
        if (metric == Metric.PM25_1H) return value(metric, region)?.let(Band::ofHourlyPm25)
        val index = when (metric) {
            Metric.PSI -> psi
            Metric.PM25 -> pm25Sub
            Metric.PM10 -> pm10Sub
            Metric.PM25_1H -> emptyMap()
        }[region.key] ?: value(metric, region)
        return index?.let(Band::of)
    }

    fun highest(metric: Metric): Region? =
        Region.entries.filter { value(metric, it) != null }.maxByOrNull { value(metric, it)!! }

    private fun values(metric: Metric) = when (metric) {
        Metric.PSI -> psi
        Metric.PM25 -> pm25
        Metric.PM10 -> pm10
        Metric.PM25_1H -> pm25Hourly
    }
}
