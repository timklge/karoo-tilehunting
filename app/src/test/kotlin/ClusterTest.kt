package de.timklge.karootilehunting

import org.junit.Test

class ClusterTest {
    @Test
    fun testCluster(){
        val centerTile = Tile(8815, 5481)
        val tileLoadRadius = 5
        val tiles = setOf(Tile(8815, 5481))

        val viewSquare = Square(centerTile.x - tileLoadRadius, centerTile.y - tileLoadRadius, tileLoadRadius * 2)
        val tileLoadRange = centerTile.x - tileLoadRadius..centerTile.x + tileLoadRadius

        val largestSquare = getSquare(tiles)

        val tilesInSquare = tiles.filter { largestSquare?.isInside(it) == true }.toSet()
        val tilesNotInSquare = tiles - tilesInSquare
        val unexploredTiles = viewSquare.getAllTiles() - tiles

        val squareCluster = clusterTiles(tilesInSquare).single()
        val clusteredExploredTiles = clusterTiles(tilesNotInSquare)
        val clusteredUnexploredTiles = clusterTiles(unexploredTiles)

        val squareClusterGridLines = squareCluster.getGridPolylines()

    }
}