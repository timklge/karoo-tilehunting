package de.timklge.karootilehunting

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import de.timklge.karootilehunting.screens.MainScreen
import de.timklge.karootilehunting.theme.AppTheme
import de.timklge.karootilehunting.UserPreferencesSerializer
import de.timklge.karootilehunting.data.UserPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
