package de.timklge.karootilehunting

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import de.timklge.karootilehunting.services.ExploreTilesService.Companion.margin
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh

@Serializable
data class Tile(val x: Int, val y: Int) {
    fun isNeighbour(tile: Tile): Boolean {
        return (this.x == tile.x && (this.y == tile.y + 1 || this.y == tile.y - 1)) ||
                (this.y == tile.y && (this.x == tile.x + 1 || this.x == tile.x - 1))
    }

    fun isSurrounded(tiles: Set<Tile>): Boolean {
        return tiles.contains(Tile(x + 1, y)) &&
                tiles.contains(Tile(x - 1, y)) &&
                tiles.contains(Tile(x, y + 1)) &&
                tiles.contains(Tile(x, y - 1))
    }

    fun getLon(zoom: Int = 14): Double {
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun getLat(zoom: Int = 14): Double {
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(Math.PI * (1 - 2 * y / n)))
        return Math.toDegrees(latRad)
    }

    fun getCorners(): List<Point> {
        return listOf(
            CurrentCorner.TOP_LEFT.getCoords(this),
            CurrentCorner.TOP_RIGHT.getCoords(this),
            CurrentCorner.BOTTOM_RIGHT.getCoords(this),
            CurrentCorner.BOTTOM_LEFT.getCoords(this)
        )
    }

    fun isInbounds(lng: Double, lat: Double): Boolean {
        val tileCorners = getCorners()

        val point = Point.fromLngLat(normalizeLongitude(lng), lat)

        // Normalize longitudes of tile corners
        val normalizedCorners = tileCorners.map { Point.fromLngLat(normalizeLongitude(it.longitude()), it.latitude()) }

        // Check if point is inside the tile boundaries with margin
        return point.longitude() > normalizedCorners[0].longitude() + margin &&
                point.longitude() < normalizedCorners[1].longitude() - margin &&
                point.latitude() < normalizedCorners[0].latitude() - margin &&
                point.latitude() > normalizedCorners[3].latitude() + margin
    }

    private fun normalizeLongitude(lng: Double): Double {
        return ((lng + 180) % 360 + 360) % 360 - 180
    }
}

enum class CurrentCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    // Returns the actual (unmodified) coordinates for the given tile corner.
    fun getCoords(tile: Tile): Point {
        return when (this) {
            TOP_LEFT -> Point.fromLngLat(tile.getLon(), tile.getLat())
            TOP_RIGHT -> Point.fromLngLat(Tile(tile.x + 1, tile.y).getLon(), tile.getLat())
            BOTTOM_LEFT -> Point.fromLngLat(tile.getLon(), Tile(tile.x, tile.y + 1).getLat())
            BOTTOM_RIGHT -> Point.fromLngLat(Tile(tile.x + 1, tile.y).getLon(), Tile(tile.x, tile.y + 1).getLat())
        }
    }
}

/**
 * The Cluster class represents a collection of contiguous tiles.
 * The outlines are computed by collecting the boundary edges that do not have neighbouring tiles.
 * Then each boundary segment is inset toward the tile interior by a given offset.
 * Finally, the inset segments are chained into closed polylines.
 */
open class Cluster {
    val tiles = mutableSetOf<Tile>()

    /**
     * Computes the cluster outlines. Each outline is a closed polyline represented as a LineString.
     *
     * The insetOffset is provided in meters and will be converted to degrees.
     */
    fun getPolyline(insetOffset: Double = 50.0): List<LineString> {
        if (tiles.isEmpty()) return emptyList()

        val insetDegrees = TurfConversion.lengthToDegrees(insetOffset, TurfConstants.UNIT_METERS)
        val segments = mutableListOf<Pair<Point, Point>>()

        for (tile in tiles) {
            val topLeft = CurrentCorner.TOP_LEFT.getCoords(tile)
            val topRight = CurrentCorner.TOP_RIGHT.getCoords(tile)
            val bottomLeft = CurrentCorner.BOTTOM_LEFT.getCoords(tile)
            val bottomRight = CurrentCorner.BOTTOM_RIGHT.getCoords(tile)

            // Calculate corner points with appropriate insets
            val noNorth = !hasNorthNeighbor(tile)
            val noEast = !hasEastNeighbor(tile)
            val noSouth = !hasSouthNeighbor(tile)
            val noWest = !hasWestNeighbor(tile)

            // Calculate inset points considering both dimensions
            val inTopLeft = Point.fromLngLat(
                topLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                topLeft.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inTopRight = Point.fromLngLat(
                topRight.longitude() - (if (noEast) insetDegrees else 0.0),
                topRight.latitude() - (if (noNorth) insetDegrees else 0.0)
            )
            val inBottomLeft = Point.fromLngLat(
                bottomLeft.longitude() + (if (noWest) insetDegrees else 0.0),
                bottomLeft.latitude() + (if (noSouth) insetDegrees else 0.0)
            )
            val inBottomRight = Point.fromLngLat(
                bottomRight.longitude() - (if (noEast) insetDegrees else 0.0),
                bottomRight.latitude() + (if (noSouth) insetDegrees else 0.0)
            )

            // Add segments for exposed edges
            if (noNorth) segments.add(inTopLeft to inTopRight)
            if (noEast) segments.add(inTopRight to inBottomRight)
            if (noSouth) segments.add(inBottomLeft to inBottomRight)
            if (noWest) segments.add(inTopLeft to inBottomLeft)
        }

        val polylines = chainSegments(segments)
        return polylines.map { pts -> LineString.fromLngLats(pts) }
    }

    // Check for neighbouring tiles in each direction using the tile grid.
    private fun hasNorthNeighbor(tile: Tile) = tiles.any { it.x == tile.x && it.y == tile.y - 1 }
    private fun hasEastNeighbor(tile: Tile) = tiles.any { it.x == tile.x + 1 && it.y == tile.y }
    private fun hasSouthNeighbor(tile: Tile) = tiles.any { it.x == tile.x && it.y == tile.y + 1 }
    private fun hasWestNeighbor(tile: Tile) = tiles.any { it.x == tile.x - 1 && it.y == tile.y }

    private fun chainSegments(segments: List<Pair<Point, Point>>): List<List<Point>> {
        if (segments.isEmpty()) return emptyList()

        val remaining = segments.toMutableList()
        val polylines = mutableListOf<MutableList<Point>>()

        while (remaining.isNotEmpty()) {
            val currentPolyline = mutableListOf<Point>()
            val firstSeg = remaining.removeAt(0)
            currentPolyline.add(firstSeg.first)
            currentPolyline.add(firstSeg.second)

            var isClosed = false
            var changed = true
            while (changed && !isClosed) {
                changed = false
                for (i in remaining.indices.reversed()) {
                    val seg = remaining[i]
                    val start = currentPolyline.first()
                    val end = currentPolyline.last()

                    when {
                        pointsEqual(seg.first, end) -> {
                            currentPolyline.add(seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, end) -> {
                            currentPolyline.add(seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.first, start) -> {
                            currentPolyline.add(0, seg.second)
                            remaining.removeAt(i)
                            changed = true
                        }
                        pointsEqual(seg.second, start) -> {
                            currentPolyline.add(0, seg.first)
                            remaining.removeAt(i)
                            changed = true
                        }
                    }
                    if (pointsEqual(currentPolyline.first(), currentPolyline.last())) {
                        isClosed = true
                        break
                    }
                }
            }
            //if (!pointsEqual(currentPolyline.first(), currentPolyline.last())) {
            //    currentPolyline.add(currentPolyline.first())
            //}
            polylines.add(currentPolyline)
        }
        return polylines
    }

    // Checks if two points are effectively equal using a small epsilon.
    private fun pointsEqual(p1: Point, p2: Point, epsilon: Double = 1e-6): Boolean {
        return abs(p1.longitude() - p2.longitude()) < epsilon &&
                abs(p1.latitude() - p2.latitude()) < epsilon
    }

    /**
     * Returns a list of LineString objects representing the grid lines between adjacent tiles in the cluster.
     * This method creates one line per shared edge and merges collinear adjacent segments.
     */
    fun getGridPolylines(): List<LineString> {
        // Temporary data class to hold segment info for merging.
        data class Segment(val fixed: Double, var start: Double, var end: Double)

        val verticalSegments = mutableListOf<Segment>()
        val horizontalSegments = mutableListOf<Segment>()

        // For each tile, check right and bottom neighbours to draw an internal edge.
        for (tile in tiles) {
            // Vertical shared edge.
            if (tiles.contains(Tile(tile.x + 1, tile.y))) {
                val top = CurrentCorner.TOP_RIGHT.getCoords(tile).latitude()
                val bottom = CurrentCorner.BOTTOM_RIGHT.getCoords(tile).latitude()
                val lng = CurrentCorner.TOP_RIGHT.getCoords(tile).longitude()
                verticalSegments.add(Segment(lng, min(top, bottom), max(top, bottom)))
            }
            // Horizontal shared edge.
            if (tiles.contains(Tile(tile.x, tile.y + 1))) {
                val left = CurrentCorner.BOTTOM_LEFT.getCoords(tile).longitude()
                val right = CurrentCorner.BOTTOM_RIGHT.getCoords(tile).longitude()
                val lat = CurrentCorner.BOTTOM_LEFT.getCoords(tile).latitude()
                horizontalSegments.add(Segment(lat, min(left, right), max(left, right)))
            }
        }

        // Merge contiguous vertical segments.
        val mergedVertical = verticalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        // Merge contiguous horizontal segments.
        val mergedHorizontal = horizontalSegments.groupBy { it.fixed }.flatMap { (_, segments) ->
            segments.sortedBy { it.start }.fold(mutableListOf<Segment>()) { acc, seg ->
                if (acc.isEmpty()) {
                    acc.add(seg)
                } else {
                    val last = acc.last()
                    if (abs(seg.start - last.end) < 1e-6 || seg.start <= last.end) {
                        last.end = max(last.end, seg.end)
                    } else {
                        acc.add(seg)
                    }
                }
                acc
            }
        }

        val gridLines = mutableListOf<LineString>()

        // Vertical segments: fixed value is longitude, start/end represent latitude.
        for (seg in mergedVertical) {
            val p1 = Point.fromLngLat(seg.fixed, seg.start)
            val p2 = Point.fromLngLat(seg.fixed, seg.end)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }

        // Horizontal segments: fixed value is latitude, start/end represent longitude.
        for (seg in mergedHorizontal) {
            val p1 = Point.fromLngLat(seg.start, seg.fixed)
            val p2 = Point.fromLngLat(seg.end, seg.fixed)
            gridLines.add(LineString.fromLngLats(listOf(p1, p2)))
        }
        return gridLines
    }
}

// Groups a set of tiles into clusters of contiguous tiles.
fun clusterTiles(tiles: Set<Tile>): List<Cluster> {
    val clusters = mutableListOf<Cluster>()
    val remainingTiles = tiles.toMutableSet()

    while (remainingTiles.isNotEmpty()) {
        val seedTile = remainingTiles.first()
        val newCluster = Cluster()
        val tilesToAdd = mutableSetOf(seedTile)

        remainingTiles.remove(seedTile)

        var tilesAdded = true
        while (tilesAdded) {
            tilesAdded = false
            val newlyAddedTiles = mutableSetOf<Tile>()

            for (tile in tilesToAdd) {
                if (newCluster.tiles.add(tile)) {
                    newlyAddedTiles.add(tile)
                }
            }

            for (tile in newlyAddedTiles) {
                val neighbours = remainingTiles.filter { it.isNeighbour(tile) }
                tilesToAdd.addAll(neighbours)
                remainingTiles.removeAll(neighbours)
                if (neighbours.isNotEmpty()) {
                    tilesAdded = true
                }
            }
        }
        clusters.add(newCluster)
    }
    return clusters
}