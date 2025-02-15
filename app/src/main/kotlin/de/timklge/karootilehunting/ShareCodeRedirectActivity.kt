package de.timklge.karootilehunting

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import de.timklge.karootilehunting.datastores.userPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareCodeRedirectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        Log.i(KarooTilehuntingExtension.TAG, "Received redirect: $uri")

        val shareCodeRegex = Regex("share/(.+)")

        if (uri != null) {
            shareCodeRegex.find(uri.toString())?.let { match ->
                val shareCode = match.groupValues[1]
                Log.i(KarooTilehuntingExtension.TAG, "Share code: $shareCode")

                CoroutineScope(Dispatchers.Main).launch {
                    userPreferencesDataStore.updateData {
                        it.toBuilder().setStatshuntersSharecode(shareCode).build()
                    }
                    Log.i(KarooTilehuntingExtension.TAG, "Updated share code")
                    finish()
                }
            }
        } else {
            finish()
        }
    }
}