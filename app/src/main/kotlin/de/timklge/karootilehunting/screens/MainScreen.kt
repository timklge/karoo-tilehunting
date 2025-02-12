package de.timklge.karootilehunting.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.exploredTilesDataStore
import de.timklge.karootilehunting.getSquare
import de.timklge.karootilehunting.userPreferencesDataStore
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class TileDrawRangeEnum(val radius: Int){
    LOAD_2(2),
    LOAD_3(3),
    LOAD_4(4),
    LOAD_5(5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var karooConnected by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(ctx) }

    val exploredTilesStore by ctx.exploredTilesDataStore.data.collectAsStateWithLifecycle(null)
    val settingsStore by ctx.userPreferencesDataStore.data.collectAsStateWithLifecycle(null)

    var statshuntersDialogVisible by remember { mutableStateOf(false) }

    var downloadedActivities by remember { mutableIntStateOf(0) }
    var exploredTilesCount by remember { mutableIntStateOf(0) }
    var squareSize by remember { mutableIntStateOf(0) }
    var recentTilesCount by remember { mutableIntStateOf(0) }

    var savedDialogVisible by remember { mutableStateOf(false) }
    var clearedRecentExploredTilesDialogVisible by remember { mutableStateOf(false) }
    var tileLoadRange by remember { mutableStateOf("3") }
    var hideGrid by remember { mutableStateOf(false) }

    LaunchedEffect(exploredTilesStore) {
        coroutineScope.launch {
            downloadedActivities = exploredTilesStore?.downloadedActivities ?: 0
            val exploredTiles = exploredTilesStore?.exploredTilesList?.map { Tile(it.x, it.y) }?.toSet()
            recentTilesCount = exploredTilesStore?.recentlyExploredTilesCount ?: 0
            exploredTilesCount = exploredTiles?.size ?: 0
            squareSize = exploredTiles?.let { getSquare(it)?.size } ?: 0
            hideGrid = settingsStore?.hideGridLines ?: false
        }
    }

    LaunchedEffect(settingsStore){
        coroutineScope.launch {
            val tileDrawRange = settingsStore?.tileDrawRange?.let { if(it == 0) 3 else it } ?: 3
            tileLoadRange = "${tileDrawRange.coerceIn(2..5)}"
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text("Tilehunting") })
        Column(
            modifier = Modifier
                .padding(5.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Activities:")
                    Text("Tiles:")
                    Text("Square:")
                    Text("Recent:")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("$downloadedActivities")
                    Text("$exploredTilesCount")
                    Text("$squareSize")
                    Text("$recentTilesCount")
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
                    Text("Force reload")
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

            FilledTonalButton(modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), onClick = {

                coroutineScope.launch {
                    ctx.userPreferencesDataStore.updateData { preferences ->
                        preferences.toBuilder()
                            .setTileDrawRange(tileLoadRange.toInt())
                            .setHideGridLines(hideGrid)
                            .build()
                    }
                    savedDialogVisible = true
                }
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

            if (savedDialogVisible){
                AlertDialog(onDismissRequest = { savedDialogVisible = false },
                    confirmButton = { Button(onClick = {
                        savedDialogVisible = false
                    }) { Text("OK") } },
                    text = { Text("Settings saved successfully.") }
                )
            }

            if (clearedRecentExploredTilesDialogVisible){
                AlertDialog(onDismissRequest = { savedDialogVisible = false },
                    confirmButton = { Button(onClick = {
                        savedDialogVisible = false
                    }) { Text("OK") } },
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

                            Text("Go to statshunters.com/share and create a link that shares activities. Enter its sharing code below.")

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
    }
}