package com.psiwidget.location

import com.psiwidget.data.LatLng
import com.psiwidget.data.Region
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RegionResolver {
    /** The API's labelLocation points, used until a response supplies its own. */
    val DEFAULT_LOCATIONS = mapOf(
        "north" to LatLng(1.41803, 103.82),
        "south" to LatLng(1.29587, 103.82),
        "east" to LatLng(1.35735, 103.94),
        "west" to LatLng(1.35735, 103.70),
        "central" to LatLng(1.35735, 103.82),
    )

    /** Beyond this, the user is not in Singapore and no region is "near" them. */
    private const val MAX_DISTANCE_KM = 40.0

    fun nearest(latitude: Double, longitude: Double, locations: Map<String, LatLng> = DEFAULT_LOCATIONS): Region? =
        locations.mapNotNull { (key, point) ->
            Region.fromKey(key)?.let { it to distanceKm(latitude, longitude, point.latitude, point.longitude) }
        }
            .filter { it.second <= MAX_DISTANCE_KM }
            .minByOrNull { it.second }
            ?.first

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }
}
