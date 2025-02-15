package de.timklge.karootilehunting

import kotlinx.serialization.Serializable

data class Square(val x: Int, val y: Int, val size: Int){
    fun isInside(tile: Tile): Boolean {
        return tile.x >= x && tile.x < x + size && tile.y >= y && tile.y < y + size
    }

    fun getAllTiles(): Set<Tile> = buildSet {
        for (i in 0..<this@Square.size){
            for (j in 0..<this@Square.size){
                add(Tile(x + i, y + j))
            }
        }
    }

    fun isInside(lat: Double, lon: Double): Boolean {
        return isInside(coordsToTile(lat, lon))
    }

    companion object {
        /**
         * Returns the largest square (represented by its top–left corner and size)
         * that can be entirely found in the specified set of tiles.
         *
         * In this implementation, each tile is interpreted as the top–left corner of a candidate square.
         * The square extends to the right (increasing x) and downward (increasing y).
         */
        fun getBiggestSquare(tiles: Set<Tile>): Square? {
            if (tiles.isEmpty()) return null

            var bestSquare: Square? = null

            // For each tile, consider it as the top–left of a candidate square.
            for (tile in tiles) {
                var s = 1
                // Expand the square until a required tile is missing.
                while (true) {
                    // Check all positions in the candidate square.
                    val squareComplete = (0 until s).all { i ->
                        (0 until s).all { j ->
                            // For a square with top–left corner at tile,
                            // the tiles needed are those at (tile.x + i, tile.y + j)
                            Tile(tile.x + i, tile.y + j) in tiles
                        }
                    }
                    if (squareComplete) {
                        // Update bestSquare if this square is larger than the current best.
                        if (bestSquare == null || s > bestSquare.size) {
                            bestSquare = Square(tile.x, tile.y, s)
                        }
                        s++
                    } else {
                        break
                    }
                }
            }
            return bestSquare
        }
    }
}