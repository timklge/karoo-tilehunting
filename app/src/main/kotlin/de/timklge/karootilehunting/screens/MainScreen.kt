package de.timklge.karootilehunting.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.R
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.data.Badge
import de.timklge.karootilehunting.data.GpsCoords
import de.timklge.karootilehunting.datastores.achievementsDataStore
import de.timklge.karootilehunting.datastores.exploredTilesDataStore
import de.timklge.karootilehunting.datastores.userPreferencesDataStore
import de.timklge.karootilehunting.lastKnownGpsCoordsDataStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.Symbol
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

enum class TileDrawRangeEnum(val radius: Int){
    LOAD_2(2),
    LOAD_3(3),
    LOAD_4(4),
    LOAD_5(5)
}

fun parseInstant(dateString: String?): Instant? {
    if (dateString.isNullOrBlank()) return null

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    return Instant.from(formatter.parse(dateString))
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> {
    return callbackFlow {
        val listenerId = addConsumer { userProfile: UserProfile ->
            trySendBlocking(userProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

@Composable
fun DrawBadge(lastKnownPositionStore: GpsCoords?, badge: Badge, profile: UserProfile?, karooSystemService: KarooSystemService){
    Row(modifier = Modifier.fillMaxWidth().height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!badge.achievedAt.isNullOrBlank()) {
            Icon(Icons.Default.Done, contentDescription = "Achieved", modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Default.Clear, contentDescription = "Not achieved", modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(5.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(badge.name, maxLines = 1, overflow = TextOverflow.Ellipsis)

            val achievedAt = parseInstant(badge.achievedAt)
            val subtitle = buildList {
                if (badge.coordinates != null && lastKnownPositionStore != null && (badge.coordinates.longitude != 0.0 && badge.coordinates.latitude != 0.0)) {
                    val coords = Point.fromLngLat(badge.coordinates.longitude, badge.coordinates.latitude)
                    val lastKnownPosition = lastKnownPositionStore?.let { lastKnownPositionStore -> Point.fromLngLat(lastKnownPositionStore.longitude, lastKnownPositionStore.latitude) }

                    if (lastKnownPosition != null) {
                        val distance = TurfMeasurement.distance(coords, lastKnownPosition, TurfConstants.UNIT_METERS)

                        if (profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) {
                            add("${(distance / 1609.34).toInt()}mi")
                        } else {
                            add("${(distance / 1000).toInt()}km")
                        }
                    }
                }

                if (achievedAt != null) add(badge.achievedAt.toString())
            }.joinToString(" - ")

            if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), color = Color.LightGray)
        }

        if (badge.coordinates != null && badge.coordinates.longitude != 0.0 && badge.coordinates.latitude != 0.0){
            Spacer(modifier = Modifier.width(5.dp))

            Icon(Icons.Default.LocationOn, contentDescription = "Navigate", modifier = Modifier.size(24.dp).clickable {
                karooSystemService.dispatch(LaunchPinDrop(Symbol.POI(
                    id = "${badge.id}",
                    lat = badge.coordinates.latitude,
                    lng = badge.coordinates.longitude,
                    name = "Navigate to ${badge.name}",
                )))
            })
        }
    }
}

data class CustomTune(val freq: Int, val duration: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exploredTilesStore by ctx.exploredTilesDataStore.data.collectAsStateWithLifecycle(null)
    val settingsStore by ctx.userPreferencesDataStore.data.collectAsStateWithLifecycle(null)
    val badgesStore by ctx.achievementsDataStore.data.collectAsStateWithLifecycle(null)
    val lastKnownPositionStore by ctx.lastKnownGpsCoordsDataStore.data.collectAsStateWithLifecycle(null)

    var statshuntersDialogVisible by remember { mutableStateOf(false) }

    var downloadedActivities by remember { mutableIntStateOf(0) }
    var exploredTilesCount by remember { mutableIntStateOf(0) }
    var squareSize by remember { mutableIntStateOf(0) }
    var recentTilesCount by remember { mutableIntStateOf(0) }

    var clearedRecentExploredTilesDialogVisible by remember { mutableStateOf(false) }
    var tileLoadRange by remember { mutableStateOf("3") }
    var hideGrid by remember { mutableStateOf(false) }
    var isDisabled by remember { mutableStateOf(false) }
    var showActivityLines by remember { mutableStateOf(false) }
    var disableTileAlertSound by remember { mutableStateOf(false) }
    var playCustomTileAlertSound by remember { mutableStateOf(false) }
    val playCustomTileAlertSoundTunes = remember { mutableStateListOf<CustomTune>() }

    var availableBadgesCount by remember { mutableIntStateOf(0) }
    var badgesCount by remember { mutableIntStateOf(0) }
    var badgesError by remember { mutableStateOf("") }
    var badgesWithCoordsList by remember { mutableStateOf(listOf<Badge>()) }
    var badgesWithoutCoordsList by remember { mutableStateOf(listOf<Badge>()) }

    val karooSystemService by remember { mutableStateOf(KarooSystemService(ctx)) }
    val profile by karooSystemService.streamUserProfile().collectAsStateWithLifecycle(null)

    val tabs = listOf("Settings", "Badges")
    var tabIndex by remember { mutableIntStateOf(0) }

    suspend fun updateSettings(){
        Log.d(KarooTilehuntingExtension.TAG, "Updating settings")

        ctx.userPreferencesDataStore.updateData { preferences: de.timklge.karootilehunting.data.UserPreferences ->
            val protoCustomSounds = playCustomTileAlertSoundTunes.map { tune: CustomTune ->
                de.timklge.karootilehunting.data.CustomTune.newBuilder()
                    .setFreq(tune.freq)
                    .setDuration(tune.duration)
                    .build()
            }

            preferences.toBuilder()
                .setTileDrawRange(tileLoadRange.toInt())
                .setHideGridLines(hideGrid)
                .setIsDisabled(isDisabled)
                .setShowActivityLines(showActivityLines)
                .setDisableTileAlertSound(disableTileAlertSound)
                .clearCustomTileExploreSound()
                .addAllCustomTileExploreSound(protoCustomSounds)
                .setEnableCustomTileExploreSound(playCustomTileAlertSound)
                .build()
        }
    }

    DisposableEffect(karooSystemService) {
        onDispose {
            Log.d(KarooTilehuntingExtension.TAG, "Disconnecting Karoo system service")
            karooSystemService.disconnect()
        }
    }

    LaunchedEffect(karooSystemService) {
        karooSystemService.connect {
            Log.d(KarooTilehuntingExtension.TAG, "Karoo system service connected")
        }
    }

    LaunchedEffect(exploredTilesStore) {
        coroutineScope.launch {
            downloadedActivities = exploredTilesStore?.downloadedActivities ?: 0
            val exploredTiles = exploredTilesStore?.exploredTilesList?.map { Tile(it.x, it.y) }?.toSet()
            recentTilesCount = exploredTilesStore?.recentlyExploredTilesCount ?: 0
            exploredTilesCount = exploredTiles?.size ?: 0
            squareSize = exploredTilesStore?.biggestSquareSize ?: 0
            hideGrid = settingsStore?.hideGridLines == true
            isDisabled = settingsStore?.isDisabled == true
            showActivityLines = settingsStore?.showActivityLines == true
            disableTileAlertSound = settingsStore?.disableTileAlertSound == true
            playCustomTileAlertSound = settingsStore?.enableCustomTileExploreSound == true

            val loadedSounds = settingsStore?.customTileExploreSoundList?.map { CustomTune(it.freq, it.duration) }
            if (loadedSounds != null) {
                if (playCustomTileAlertSoundTunes.toList() != loadedSounds) {
                    playCustomTileAlertSoundTunes.clear()
                    playCustomTileAlertSoundTunes.addAll(loadedSounds)
                }
            } else {
                if (playCustomTileAlertSoundTunes.isNotEmpty()) {
                    playCustomTileAlertSoundTunes.clear()
                }
            }
        }
    }

    LaunchedEffect(badgesStore) {
        coroutineScope.launch {
            badgesError = badgesStore?.error ?: ""
            availableBadgesCount = badgesStore?.badgesCount ?: 0
            badgesCount = badgesStore?.badgesList?.count { !it.achievedAt.isNullOrBlank() } ?: 0
            badgesWithCoordsList = badgesStore?.badgesList?.filter { badge ->
                val coords = badge.coordinates?.let { coords -> Point.fromLngLat(coords.longitude, coords.latitude) }?.let { if (it.longitude() != 0.0 && it.latitude() != 0.0) it else null }
                val lastKnownPosition = lastKnownPositionStore
                    ?.let { lastKnownPositionStore -> Point.fromLngLat(lastKnownPositionStore.longitude, lastKnownPositionStore.latitude) }
                    ?.let { if (it.longitude() != 0.0 && it.latitude() != 0.0) it else null }
                val distance = if (coords != null && lastKnownPosition != null) TurfMeasurement.distance(coords, lastKnownPosition, TurfConstants.UNIT_METERS) else null

                distance != null && distance < 200_000
            }?.sortedBy { badge ->
                val coords = badge.coordinates?.let { coords -> Point.fromLngLat(coords.longitude, coords.latitude) }
                val lastKnownPosition = lastKnownPositionStore?.let { lastKnownPositionStore -> Point.fromLngLat(lastKnownPositionStore.longitude, lastKnownPositionStore.latitude) }

                if (coords != null && lastKnownPosition != null) TurfMeasurement.distance(coords, lastKnownPosition, TurfConstants.UNIT_METERS) else 0.0
            } ?: emptyList()

            badgesWithoutCoordsList = badgesStore?.badgesList?.filter { it.coordinates == null || (it.coordinates.latitude == 0.0 || it.coordinates.longitude == 0.0)  }?.sortedBy { badge ->
                parseInstant(badge.achievedAt)
            } ?: emptyList()
        }
    }

    LaunchedEffect(settingsStore){
        coroutineScope.launch {
            val tileDrawRange = settingsStore?.tileDrawRange?.let { if(it == 0) 3 else it } ?: 3
            tileLoadRange = "${tileDrawRange.coerceIn(2..5)}"
        }
    }

    Box(modifier = Modifier.fillMaxSize()){
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }

                if (tabIndex == 0) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Activities:")
                                Text("Tiles:")
                                Text("Square:")
                                Text("Recent:")
                                Text("Badges:")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$downloadedActivities")
                                Text("$exploredTilesCount")
                                Text("$squareSize")
                                Text("$recentTilesCount")

                                if (badgesError.isBlank()) {
                                    if (availableBadgesCount != 0){
                                        Text("$badgesCount / $availableBadgesCount")
                                    } else {
                                        Text("$badgesCount")
                                    }
                                } else {
                                    Text(badgesError)
                                }
                            }
                        }

                        if (exploredTilesStore?.isDownloading == true){
                            Text("Loaded ${exploredTilesStore?.downloadedActivities ?: 0} activities...")
                            LinearProgressIndicator()
                        } else {
                            val lastDownloadedAtTimestamp = exploredTilesStore?.lastDownloadedAt ?: 0
                            val lastDownloadedAt = DateFormat.getDateTimeInstance().format(Date(lastDownloadedAtTimestamp))

                            if (!exploredTilesStore?.lastDownloadError.isNullOrBlank()){
                                val atString = if (lastDownloadedAtTimestamp > 0) " at $lastDownloadedAt" else ""
                                Text("Error downloading activities: ${exploredTilesStore?.lastDownloadError}${atString}.")
                            } else if ((exploredTilesStore?.downloadedActivities ?: 0) > 0 && lastDownloadedAtTimestamp > 0){
                                Text("Last successful download at ${lastDownloadedAt}.")
                            } else {
                                Text("No activities downloaded yet.")
                            }
                        }

                        if (exploredTilesStore?.isDownloading != true && !settingsStore?.statshuntersSharecode.isNullOrBlank()){
                            FilledTonalButton(modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                                onClick = {
                                    coroutineScope.launch {
                                        ctx.exploredTilesDataStore.updateData { exploredTiles ->
                                            exploredTiles.toBuilder().setLastDownloadedAt(0).build()
                                        }
                                    }
                                }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Update Tiles")
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Download new activities")
                            }
                        }

                        if (recentTilesCount > 0) {
                            FilledTonalButton(modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp), onClick = {

                                coroutineScope.launch {
                                    ctx.exploredTilesDataStore.updateData { exploredTiles ->
                                        exploredTiles.toBuilder()
                                            .clearRecentlyExploredTiles()
                                            .build()
                                    }
                                    clearedRecentExploredTilesDialogVisible = true
                                }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Reset recent tiles")
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Reset recent tiles")
                            }
                        }

                        FilledTonalButton(modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                            onClick = {
                                statshuntersDialogVisible = true
                            }) {
                            Icon(Icons.Default.Person, contentDescription = "Connect StatsHunters")
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Connect StatsHunters")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = !isDisabled, onCheckedChange = { isDisabled = !it})
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Enable tile drawing")
                        }

                        if (!isDisabled){
                            apply {
                                val dropdownOptions = TileDrawRangeEnum.entries.toList().map { unit -> DropdownOption("${unit.radius}", "${unit.radius}") }
                                val dropdownInitialSelection by remember(tileLoadRange) {
                                    mutableStateOf(dropdownOptions.find { option -> option.id == tileLoadRange } ?: dropdownOptions[0])
                                }
                                Dropdown(label = "Tile Draw Range", options = dropdownOptions, selected = dropdownInitialSelection) { selectedOption ->
                                    tileLoadRange = selectedOption.id
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = !hideGrid, onCheckedChange = { hideGrid = !it})
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Show grid")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = showActivityLines, onCheckedChange = { showActivityLines = it})
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Show activity lines")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = !disableTileAlertSound, onCheckedChange = { disableTileAlertSound = !it})
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Play alert sound")
                            }

                            if (!disableTileAlertSound) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = playCustomTileAlertSound, onCheckedChange = { playCustomTileAlertSound = it})
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Custom sound sequence")
                                }

                                if (playCustomTileAlertSound){
                                    playCustomTileAlertSoundTunes.forEachIndexed { index, tune ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = tune.freq.toString(),
                                                onValueChange = { textValue ->
                                                    textValue.toIntOrNull()?.let { newFreq ->
                                                        if (index < playCustomTileAlertSoundTunes.size) {
                                                            playCustomTileAlertSoundTunes[index] = tune.copy(freq = newFreq)
                                                        }
                                                    }
                                                },
                                                label = { Text("Freq (Hz)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = tune.duration.toString(),
                                                onValueChange = { textValue ->
                                                    textValue.toIntOrNull()?.let { newDuration ->
                                                        if (index < playCustomTileAlertSoundTunes.size) {
                                                            playCustomTileAlertSoundTunes[index] = tune.copy(duration = newDuration)
                                                        }
                                                    }
                                                },
                                                label = { Text("Duration (ms)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            IconButton(onClick = {
                                                if (index < playCustomTileAlertSoundTunes.size) {
                                                    playCustomTileAlertSoundTunes.removeAt(index)
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove Tone")
                                            }
                                        }
                                    }

                                    val defaultTone = if (karooSystemService.hardwareType == HardwareType.K2) {
                                        CustomTune(2000, 200)
                                    } else {
                                        CustomTune(440, 200)
                                    }

                                    FilledTonalButton(
                                        onClick = { playCustomTileAlertSoundTunes.add(defaultTone) },
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Tone")
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Add Tone")
                                    }

                                    if (playCustomTileAlertSoundTunes.isNotEmpty()){
                                        FilledTonalButton(
                                            onClick = {
                                                val playTones = PlayBeepPattern(
                                                    playCustomTileAlertSoundTunes.map { tune ->
                                                        PlayBeepPattern.Tone(
                                                            frequency = tune.freq,
                                                            durationMs = tune.duration
                                                        )
                                                    }
                                                )

                                                karooSystemService.dispatch(playTones)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(50.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Tones")
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Play")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.padding(30.dp))

                        if (clearedRecentExploredTilesDialogVisible){
                            AlertDialog(onDismissRequest = { clearedRecentExploredTilesDialogVisible = false },
                                confirmButton = { Button(onClick = {
                                    clearedRecentExploredTilesDialogVisible = false
                                }, modifier = Modifier.height(50.dp)) { Text("OK") } },
                                text = { Text("Recent tiles cleared.") }
                            )
                        }

                        if (statshuntersDialogVisible){
                            Dialog(onDismissRequest = { statshuntersDialogVisible = false }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState())
                                            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        var dialogEnteredSharecode by remember { mutableStateOf("") }
                                        LaunchedEffect(Unit) {
                                            coroutineScope.launch {
                                                dialogEnteredSharecode = ctx.userPreferencesDataStore.data.first().statshuntersSharecode
                                            }
                                        }

                                        Text("Go to statshunters.com/share and create a link that shares your heatmap. Enter its sharing code below.")

                                        Text(buildAnnotatedString {
                                            append("Example: statshunters.com/share/")
                                            withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                                append("010433475e27")
                                            }
                                        })

                                        OutlinedTextField(
                                            value = dialogEnteredSharecode,
                                            onValueChange = { dialogEnteredSharecode = it },
                                            label = { Text("Code") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        FilledTonalButton(modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp), onClick = {
                                            statshuntersDialogVisible = false
                                            val sharecodeUrlRegex = Regex("share/([a-zA-Z0-9]+)")
                                            dialogEnteredSharecode = sharecodeUrlRegex.find(dialogEnteredSharecode)?.groups?.get(1)?.value ?: dialogEnteredSharecode
                                            Log.d(KarooTilehuntingExtension.TAG, "Entered sharecode: $dialogEnteredSharecode")

                                            coroutineScope.launch {
                                                var changedCode = false
                                                ctx.userPreferencesDataStore.updateData { preferences ->
                                                    changedCode = preferences.statshuntersSharecode != dialogEnteredSharecode
                                                    preferences.toBuilder().setStatshuntersSharecode(dialogEnteredSharecode).build()
                                                }
                                                if (changedCode) {
                                                    ctx.exploredTilesDataStore.updateData { exploredTiles ->
                                                        exploredTiles.toBuilder().setLastDownloadedAt(0).build()
                                                    }
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Done, contentDescription = "OK")
                                            Text("OK")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (tabIndex == 1) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (badgesError.isBlank()) {
                            if (availableBadgesCount != 0) {
                                Text("You have $badgesCount / $availableBadgesCount badges.")
                            } else {
                                Text("You have $badgesCount badges.")
                            }

                            LazyColumn {
                                items(badgesWithCoordsList.size) { index ->
                                    val badge = badgesWithCoordsList.getOrNull(index)
                                    if (badge != null) {
                                        DrawBadge(lastKnownPositionStore, badge, profile, karooSystemService)
                                    }
                                }

                                if (badgesWithoutCoordsList.isNotEmpty() && badgesWithCoordsList.isNotEmpty()){
                                    item {
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.LightGray))
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }

                                items(badgesWithoutCoordsList.size) { index ->
                                    val badge = badgesWithoutCoordsList.getOrNull(index)
                                    if (badge != null) {
                                        DrawBadge(lastKnownPositionStore, badge, profile, karooSystemService)
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.padding(30.dp))
                                }
                            }
                        } else {
                            Text(badgesError)
                        }
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                runBlocking {
                    updateSettings()
                }
            }
        }

        BackHandler {
            coroutineScope.launch {
                updateSettings()
                onFinish()
            }
        }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    onFinish()
                }
        )
    }
}
