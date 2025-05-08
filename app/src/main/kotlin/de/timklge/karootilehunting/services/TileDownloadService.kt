package de.timklge.karootilehunting.services

import android.content.Context
import android.util.Log
import de.timklge.karootilehunting.KarooTilehuntingExtension.Companion.TAG
import de.timklge.karootilehunting.Square
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.data.Activity
import de.timklge.karootilehunting.data.Badges
import de.timklge.karootilehunting.data.ExploredTiles
import de.timklge.karootilehunting.datastores.achievementsDataStore
import de.timklge.karootilehunting.datastores.activityLinesDataStore
import de.timklge.karootilehunting.datastores.exploredTilesDataStore
import de.timklge.karootilehunting.datastores.userPreferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TileDownloadService(private val applicationContext: Context, val statshuntersTilesProvider: StatshuntersTilesProvider, val statshuntersBadgesProvider: StatshuntersBadgesProvider) {
    fun startJob(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            applicationContext.exploredTilesDataStore.updateData {
                val updated = it.toBuilder().setIsDownloading(false).build()

                if (!it.lastDownloadError.isNullOrBlank()){
                    updated.toBuilder().setLastDownloadedAt(0).build()
                } else {
                    updated
                }
            }

            data class StreamData(val sharecode: String, val lastUpdatedAt: Long)

            val settingsCodeFlow = applicationContext.userPreferencesDataStore.data.map { it.statshuntersSharecode }.filter { !it.isNullOrBlank() }
            val exploredTilesFlow = applicationContext.exploredTilesDataStore.data

            combine(settingsCodeFlow, exploredTilesFlow) { sharecode, exploredTiles -> sharecode to exploredTiles }
                .map { (sharecode, exploredTiles) -> StreamData(sharecode, exploredTiles.lastDownloadedAt) }
                .distinctUntilChanged()
                .filter { (_, lastDownloadedAt) -> lastDownloadedAt < System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 7 }
                .collect {
                    Log.d(TAG, "Starting tile download job")

                    applicationContext.exploredTilesDataStore.updateData {
                        ExploredTiles.newBuilder()
                            .setIsDownloading(true)
                            .setLastDownloadError("")
                            .build()
                    }

                    applicationContext.achievementsDataStore.updateData {
                        Badges.newBuilder().build()
                    }

                    try {
                        val sharecode = applicationContext.userPreferencesDataStore.data.first().statshuntersSharecode.trim()

                        statshuntersBadgesProvider.requestBadges(sharecode).let { badges ->
                            val achievedBadgeCount = badges.badgesList.filter { !it.achievedAt.isNullOrBlank() }.size
                            Log.d(TAG, "Received ${badges.badgesCount} badges, $achievedBadgeCount achieved")

                            applicationContext.achievementsDataStore.updateData {
                                badges
                            }
                        }

                        var activityCount = 0

                        statshuntersTilesProvider.requestTiles(sharecode).collect { (activities, lines) ->
                            Log.d(TAG, "Received ${activities.size} activities with ${lines?.size} lines")
                            val hasLines = !lines.isNullOrEmpty() && lines.size == activities.size

                            applicationContext.exploredTilesDataStore.updateData { exploredTiles ->
                                val alreadyExploredTiles =
                                    exploredTiles.exploredTilesList.map { Tile(it.x, it.y) }.toSet()
                                val newTiles = activities.flatMap {
                                    it.tiles.map { Tile(it.x, it.y) }
                                }.toSet()
                                activityCount += activities.size
                                val updatedExploredTiles = alreadyExploredTiles + newTiles
                                val updatedExploredTilesProto = updatedExploredTiles.map {
                                    de.timklge.karootilehunting.data.Tile.newBuilder().setX(it.x).setY(it.y).build()
                                }
                                Log.d(TAG, "New explored tile count: ${updatedExploredTiles.size}, $activityCount activities")
                                val updatedSquare = Square.getBiggestSquare(updatedExploredTiles)
                                Log.d(TAG, "New square: $updatedSquare")

                                exploredTiles.toBuilder()
                                    .setDownloadedActivities(activityCount)
                                    .clearExploredTiles()
                                    .addAllExploredTiles(updatedExploredTilesProto)
                                    .setBiggestSquareX(updatedSquare?.x ?: 0)
                                    .setBiggestSquareY(updatedSquare?.y ?: 0)
                                    .setBiggestSquareSize(updatedSquare?.size ?: 0)
                                    .build()
                            }

                            if (hasLines) {
                                print("Adding ${lines?.size} lines to datastore")

                                val activityList = lines?.mapIndexed { index, line ->
                                    val activity = activities[index]

                                    Activity.newBuilder()
                                        .addAllTiles(activity.tiles.map { tile -> de.timklge.karootilehunting.data.Tile.newBuilder().setX(tile.x).setY(tile.y).build() })
                                        .setId(activity.id)
                                        .setDate(activity.date)
                                        .apply { if (activity.name != null) setName(activity.name) }
                                        .apply { if (activity.averageCadence != null) setAverageCadence(activity.averageCadence) }
                                        .apply { if (activity.averageHeartrate != null) setAverageHeartrate(activity.averageHeartrate) }
                                        .apply { if (activity.avg != null) setAvg(activity.avg) }
                                        .apply { if (activity.commute != null) setCommute(activity.commute) }
                                        .apply { if (activity.distance != null) setDistance(activity.distance) }
                                        .apply { if (activity.elapsedTime != null) setElapsedTime(activity.elapsedTime) }
                                        .apply { if (activity.foreignId != null) setForeignId(activity.foreignId) }
                                        .apply { if (activity.gearForeignId != null) setGearForeignId(activity.gearForeignId) }
                                        .apply { if (activity.gearId != null) setGearId(activity.gearId) }
                                        .apply { if (activity.kilojoules != null) setKilojoules(activity.kilojoules) }
                                        .apply { if (activity.lat != null) setLat(activity.lat) }
                                        .apply { if (activity.lng != null) setLng(activity.lng) }
                                        .apply { if (activity.maxHeartrate != null) setMaxHeartrate(activity.maxHeartrate) }
                                        .apply { if (activity.maxSpeed != null) setMaxSpeed(activity.maxSpeed) }
                                        .apply { if (activity.movingTime != null) setMovingTime(activity.movingTime) }
                                        .apply { if (activity.totalElevationGain != null) setTotalElevationGain(activity.totalElevationGain) }
                                        .apply { if (activity.trainer != null) setTrainer(activity.trainer) }
                                        .apply { if (activity.userId != null) setUserId(activity.userId) }
                                        .apply { if (activity.workoutType != null) setWorkoutType(activity.workoutType) }
                                        .setEncodedPolyline(line.data)
                                        .build()
                                } ?: emptyList()

                                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

                                val sortedActivityList = activityList.sortedByDescending { activity ->
                                    try {
                                        java.time.LocalDateTime.parse(activity.date, formatter)
                                    } catch (e: Throwable) {
                                        Log.w(TAG, "Failed to parse date", e)
                                        null
                                    }
                                }

                                applicationContext.activityLinesDataStore.updateData { activityLines ->
                                    val existingActivities = activityLines.activitiesList.map { it.id }.toSet()
                                    val newActivities = sortedActivityList.filter { it.id !in existingActivities }

                                    activityLines.toBuilder().addAllActivities(newActivities).build()
                                }
                            }
                        }

                        applicationContext.exploredTilesDataStore.updateData {
                            it.toBuilder().setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    } catch(e: CancellationException){
                        Log.d(TAG, "Download job cancelled")
                    } catch(e: Throwable){
                        Log.e(TAG, "Failed to download tiles", e)

                        applicationContext.exploredTilesDataStore.updateData {
                            var errorMessage = e.message ?: "Unknown error"
                            if (e is HttpDownloadError){
                                when (e.httpError) {
                                    401, 403 -> errorMessage = "Access denied"
                                    0 -> errorMessage = "No internet connection"
                                    404 -> errorMessage = "Not found"
                                }
                            }

                            it.toBuilder().setLastDownloadError(errorMessage).setIsDownloading(false).setLastDownloadedAt(System.currentTimeMillis()).build()
                        }
                    }
                }
        }
    }
}