package de.timklge.karootilehunting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.timklge.karootilehunting.screens.MainScreen
import de.timklge.karootilehunting.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen() {
                    finish()
                }
            }
        }
    }
}
