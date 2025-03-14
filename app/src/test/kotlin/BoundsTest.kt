package de.timklge.karootilehunting

import com.mapbox.geojson.Point
import org.junit.Assert
import org.junit.Test

class BoundsTest {
    @Test
    fun testBounds(){
        val onBorderBottom = Point.fromLngLat(13.739522, 51.055204)
        val onBorderBotomTile = coordsToTile(onBorderBottom.latitude(), onBorderBottom.longitude())
        val onBorderIsInbounds = onBorderBotomTile.isInbounds(onBorderBottom.longitude(), onBorderBottom.latitude())

        Assert.assertFalse("Tile on border should not be inbounds", onBorderIsInbounds)

        val insideBottom = Point.fromLngLat(13.739594, 51.055322)
        val insideBottomTile = coordsToTile(insideBottom.latitude(), insideBottom.longitude())
        val insideIsInbounds = insideBottomTile.isInbounds(insideBottom.longitude(), insideBottom.latitude())

        Assert.assertTrue("Tile inside should be inbounds", insideIsInbounds)

        val onBorderLeft = Point.fromLngLat(13.732887,51.05889)
        val onBorderLeftTile = coordsToTile(onBorderLeft.latitude(), onBorderLeft.longitude())
        val onBorderLeftIsInbounds = onBorderLeftTile.isInbounds(onBorderLeft.longitude(), onBorderLeft.latitude())

        Assert.assertFalse("Tile on border left should not be inbounds", onBorderLeftIsInbounds)

        val insideLeft = Point.fromLngLat(13.73349,51.058903)
        val insideLeftTile = coordsToTile(insideLeft.latitude(), insideLeft.longitude())
        val insideLeftIsInbounds = insideLeftTile.isInbounds(insideLeft.longitude(), insideLeft.latitude())

        Assert.assertTrue("Tile inside left should be inbounds", insideLeftIsInbounds)

        val onBorderTop = Point.fromLngLat(13.743467,51.069003)
        val onBorderTopTile = coordsToTile(onBorderTop.latitude(), onBorderTop.longitude())
        val onBorderTopIsInbounds = onBorderTopTile.isInbounds(onBorderTop.longitude(), onBorderTop.latitude())

        Assert.assertFalse("Tile on border top should not be inbounds", onBorderTopIsInbounds)

        val insideTop = Point.fromLngLat(13.743478,51.068706)
        val insideTopTile = coordsToTile(insideTop.latitude(), insideTop.longitude())
        val insideTopIsInbounds = insideTopTile.isInbounds(insideTop.longitude(), insideTop.latitude())

        Assert.assertTrue("Tile inside top should be inbounds", insideTopIsInbounds)

        val onBorderRight = Point.fromLngLat(13.754872,51.065113)
        val onBorderRightTile = coordsToTile(onBorderRight.latitude(), onBorderRight.longitude())
        val onBorderRightIsInbounds = onBorderRightTile.isInbounds(onBorderRight.longitude(), onBorderRight.latitude())

        Assert.assertFalse("Tile on border right should not be inbounds", onBorderRightIsInbounds)

        val insideRight = Point.fromLngLat(13.754545,51.065116)
        val insideRightTile = coordsToTile(insideRight.latitude(), insideRight.longitude())
        val insideRightIsInbounds = insideRightTile.isInbounds(insideRight.longitude(), insideRight.latitude())

        Assert.assertTrue("Tile inside right should be inbounds", insideRightIsInbounds)
    }
}