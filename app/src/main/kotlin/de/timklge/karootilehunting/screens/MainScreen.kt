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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karootilehunting.KarooTilehuntingExtension
import de.timklge.karootilehunting.R
import de.timklge.karootilehunting.Tile
import de.timklge.karootilehunting.datastores.exploredTilesDataStore
import de.timklge.karootilehunting.datastores.userPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
fun MainScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exploredTilesStore by ctx.exploredTilesDataStore.data.collectAsStateWithLifecycle(null)
    val settingsStore by ctx.userPreferencesDataStore.data.collectAsStateWithLifecycle(null)

    var statshuntersDialogVisible by remember { mutableStateOf(false) }

    var downloadedActivities by remember { mutableIntStateOf(0) }
    var exploredTilesCount by remember { mutableIntStateOf(0) }
    var squareSize by remember { mutableIntStateOf(0) }
    var recentTilesCount by remember { mutableIntStateOf(0) }

    var savedDialogVisible by remember { mutableStateOf(false) }
    var exitDialogVisible by remember { mutableStateOf(false) }
    var clearedRecentExploredTilesDialogVisible by remember { mutableStateOf(false) }
    var tileLoadRange by remember { mutableStateOf("3") }
    var hideGrid by remember { mutableStateOf(false) }
    var isDisabled by remember { mutableStateOf(false) }
    var showActivityLines by remember { mutableStateOf(false) }

    suspend fun updateSettings(){
        Log.d(KarooTilehuntingExtension.TAG, "Updating settings")

        ctx.userPreferencesDataStore.updateData { preferences ->
            preferences.toBuilder()
                .setTileDrawRange(tileLoadRange.toInt())
                .setHideGridLines(hideGrid)
                .setIsDisabled(isDisabled)
                .setShowActivityLines(showActivityLines)
                .build()
        }
    }

    LaunchedEffect(exploredTilesStore) {
        coroutineScope.launch {
            downloadedActivities = exploredTilesStore?.downloadedActivities ?: 0
            val exploredTiles = exploredTilesStore?.exploredTilesList?.map { Tile(it.x, it.y) }?.toSet()
            recentTilesCount = exploredTilesStore?.recentlyExploredTilesCount ?: 0
            exploredTilesCount = exploredTiles?.size ?: 0
            squareSize = exploredTilesStore?.biggestSquareSize ?: 0
            hideGrid = settingsStore?.hideGridLines ?: false
            isDisabled = settingsStore?.isDisabled ?: false
            showActivityLines = settingsStore?.showActivityLines ?: false
        }
    }

    LaunchedEffect(settingsStore){
        coroutineScope.launch {
            val tileDrawRange = settingsStore?.tileDrawRange?.let { if(it == 0) 3 else it } ?: 3
            tileLoadRange = "${tileDrawRange.coerceIn(2..5)}"
        }
    }

    Box(modifier = Modifier.fillMaxSize()){
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
                }

                if (exitDialogVisible) {
                    AlertDialog(onDismissRequest = { exitDialogVisible = false },
                        confirmButton = { Button(onClick = {
                            onFinish()
                        }) { Text("Yes") } },
                        dismissButton = { Button(onClick = {
                            exitDialogVisible = false
                        }) { Text("No") } },
                        text = { Text("Do you really want to exit?") }
                    )
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
                            clearedRecentExploredTilesDialogVisible = false
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

                Spacer(modifier = Modifier.padding(30.dp))
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