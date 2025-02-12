package de.timklge.karootilehunting

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

const val DEFAULT_ZOOM = 14

/**
 * Converts geographic coordinates to tile indices at the default zoom level.
 *
 * The formulas used are:
 *   n = 2^zoom
 *   x = floor((lon + 180) / 360 * n)
 *   y = floor((1 - ln(tan(lat_rad) + sec(lat_rad)) / π) / 2 * n)
 */
fun coordsToTile(lat: Double, lon: Double): Tile {
    val zoom = DEFAULT_ZOOM
    val n = 2.0.pow(zoom)
    val xTile = ((lon + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(lat)
    val yTile = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return Tile(xTile, yTile)
}