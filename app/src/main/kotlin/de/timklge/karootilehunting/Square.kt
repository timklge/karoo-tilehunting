/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karootilehunting

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