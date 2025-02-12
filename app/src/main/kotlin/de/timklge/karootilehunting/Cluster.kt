package de.timklge.karootilehunting

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfConversion
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

@Serializable
data class Tile(val x: Int, val y: Int) {
    fun isNeighbour(tile: Tile): Boolean {
        return (this.x == tile.x && (this.y == tile.y + 1 || this.y == tile.y - 1)) ||
                (this.y == tile.y && (this.x == tile.x + 1 || this.x == tile.x - 1))
    }

    fun getLon(zoom: Int = 14): Double {
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun getLat(zoom: Int = 14): Double {
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh( Math.PI * (1 - 2 * y / n)))
        return Math.toDegrees(latRad)
    }
}

enum class CurrentCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    // Returns an inset coordinate for the given tile corner.
    fun getCoords(tile: Tile): Point {
        return when (this) {
            TOP_LEFT -> Point.fromLngLat(tile.getLon(), tile.getLat())
            TOP_RIGHT -> Point.fromLngLat(Tile(tile.x + 1, tile.y).getLon(), tile.getLat())
            BOTTOM_LEFT -> Point.fromLngLat(tile.getLon(), Tile(tile.x, tile.y + 1).getLat())
            BOTTOM_RIGHT -> Point.fromLngLat(Tile(tile.x + 1, tile.y + 1).getLon(), Tile(tile.x, tile.y + 1).getLat())
        }
    }
}

// The Cluster class represents a collection of contiguous tiles.
open class Cluster {
    val tiles = mutableSetOf<Tile>()


    fun getPolyline(insetOffset: Double = 50.0): List<LineString> {
        if (tiles.isEmpty()) return emptyList()

        // Step 1: Compute all boundary segments
        val segments = mutableListOf<Pair<Point, Point>>()
        for (tile in tiles) {
            if (!hasNorthNeighbor(tile)) segments.add(northEdge(tile))
            if (!hasEastNeighbor(tile)) segments.add(eastEdge(tile))
            if (!hasSouthNeighbor(tile)) segments.add(southEdge(tile))
            if (!hasWestNeighbor(tile)) segments.add(westEdge(tile))
        }

        // Step 2: Group segments into closed polylines
        val polylineGroups = mutableListOf<MutableList<Point>>()
        val remainingSegments = segments.toMutableList()

        while (remainingSegments.isNotEmpty()) {
            val currentPolyline = mutableListOf<Point>()
            var currentSegment = remainingSegments.removeAt(0)
            currentPolyline.addAll(listOf(currentSegment.first, currentSegment.second))

            var extended: Boolean
            do {
                extended = false
                val lastPoint = currentPolyline.last()
                val nextSegment = findConnectedSegment(remainingSegments, lastPoint)
                nextSegment?.let {
                    remainingSegments.remove(it)
                    currentPolyline.add(if (arePointsEqual(it.first, lastPoint)) it.second else it.first)
                    extended = true
                }

                if (!extended) {
                    val firstPoint = currentPolyline.first()
                    val reverseSegment = findConnectedSegment(remainingSegments, firstPoint)
                    reverseSegment?.let {
                        remainingSegments.remove(it)
                        currentPolyline.add(0, if (arePointsEqual(it.second, firstPoint)) it.first else it.second)
                        extended = true
                    }
                }
            } while (extended)

            if (!arePointsEqual(currentPolyline.first(), currentPolyline.last())) {
                currentPolyline.add(currentPolyline.first())
            }
            polylineGroups.add(currentPolyline)
        }

        val insetDegrees = TurfConversion.lengthToDegrees(insetOffset, TurfConstants.UNIT_METERS)
        // This is the offset distance (in degrees) we’ll use when offsetting the cluster outline.
        val offsetDistance = insetDegrees / 2.0

        // Step 3: Process polylines with offset
        return polylineGroups.mapNotNull { polyline ->
            if (polyline.size < 3) return@mapNotNull null
            val isHole = isPolylineHole(polyline)
            val effectiveOffset = if (isHole) -offsetDistance else offsetDistance
            val offset = offsetPolygon(polyline, effectiveOffset, isHole)
            LineString.fromLngLats(offset)
        }
    }

    private fun offsetPolygon(polygon: List<Point>, offset: Double, isHole: Boolean): List<Point> {
        val pts = if (arePointsEqual(polygon.first(), polygon.last())) polygon.dropLast(1) else polygon
        if (pts.size < 3) return polygon

        val miterLimit = 2.0
        val offsetVertices = mutableListOf<Point>()

        for (i in pts.indices) {
            val prev = pts[(i + pts.size - 1) % pts.size]
            val curr = pts[i]
            val next = pts[(i + 1) % pts.size]

            val v1 = normalizeVector(curr.longitude() - prev.longitude(), curr.latitude() - prev.latitude())
            val v2 = normalizeVector(next.longitude() - curr.longitude(), next.latitude() - curr.latitude())

            val n1 = inwardNormal(v1, isHole)
            val n2 = inwardNormal(v2, isHole)

            val bisector = Pair(n1.first + n2.first, n1.second + n2.second)
            val bisectorLength = hypot(bisector.first, bisector.second)
            if (bisectorLength == 0.0) {
                offsetVertices.add(offsetPoint(curr, n1, offset))
                continue
            }

            val nb = Pair(bisector.first / bisectorLength, bisector.second / bisectorLength)
            val angle = acos(min(1.0, max(-1.0, n1.first * nb.first + n1.second * nb.second)))

            val scale = if (sin(angle) != 0.0) {
                val raw = offset / sin(angle)
                val maxScale = miterLimit * abs(offset)
                raw.coerceIn(-maxScale, maxScale)
            } else {
                offset
            }

            offsetVertices.add(Point.fromLngLat(
                curr.longitude() + nb.first * scale,
                curr.latitude() + nb.second * scale
            ))
        }

        offsetVertices.add(offsetVertices.first())
        return offsetVertices
    }

    private fun inwardNormal(v: Pair<Double, Double>, isHole: Boolean): Pair<Double, Double> {
        return if (!isHole) {
            Pair(v.second, -v.first)
        } else {
            Pair(-v.second, v.first)
        }.let { (x, y) ->
            val length = hypot(x, y)
            if (length == 0.0) Pair(0.0, 0.0) else Pair(x / length, y / length)
        }
    }

    private fun normalizeVector(dx: Double, dy: Double): Pair<Double, Double> {
        val length = hypot(dx, dy)
        return if (length == 0.0) Pair(0.0, 0.0) else Pair(dx / length, dy / length)
    }

    private fun offsetPoint(p: Point, normal: Pair<Double, Double>, offset: Double): Point {
        return Point.fromLngLat(
            p.longitude() + normal.first * offset,
            p.latitude() + normal.second * offset
        )
    }

    // Helper functions for boundary detection
    private fun hasNorthNeighbor(tile: Tile) = tiles.any { it.x == tile.x && it.y == tile.y - 1 }
    private fun hasEastNeighbor(tile: Tile) = tiles.any { it.x == tile.x + 1 && it.y == tile.y }
    private fun hasSouthNeighbor(tile: Tile) = tiles.any { it.x == tile.x && it.y == tile.y + 1 }
    private fun hasWestNeighbor(tile: Tile) = tiles.any { it.x == tile.x - 1 && it.y == tile.y }

    private fun northEdge(tile: Tile) =
        CurrentCorner.TOP_LEFT.getCoords(tile) to CurrentCorner.TOP_RIGHT.getCoords(tile)

    private fun eastEdge(tile: Tile) =
        CurrentCorner.TOP_RIGHT.getCoords(tile) to CurrentCorner.BOTTOM_RIGHT.getCoords(tile)

    private fun southEdge(tile: Tile) =
        CurrentCorner.BOTTOM_RIGHT.getCoords(tile) to CurrentCorner.BOTTOM_LEFT.getCoords(tile)

    private fun westEdge(tile: Tile) =
        CurrentCorner.BOTTOM_LEFT.getCoords(tile) to CurrentCorner.TOP_LEFT.getCoords(tile)

    private fun findConnectedSegment(segments: List<Pair<Point, Point>>, point: Point) =
        segments.firstOrNull { arePointsEqual(it.first, point) || arePointsEqual(it.second, point) }

    private fun isPolylineHole(polyline: List<Point>): Boolean {
        var area = 0.0
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            area += (p2.longitude() - p1.longitude()) * (p2.latitude() + p1.latitude())
        }
        return area < 0
    }

    // Checks if two points are equal, within a tolerance.
    private fun arePointsEqual(p1: Point, p2: Point, epsilon: Double = 1e-6): Boolean {
        return abs(p1.longitude() - p2.longitude()) < epsilon &&
                abs(p1.latitude() - p2.latitude()) < epsilon
    }

    // Returns a list of LineString objects representing the grid lines between adjacent tiles in the cluster.
    // This method creates one line per shared edge and merges collinear adjacent segments.
    // All grid line endpoints are no longer inset.
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