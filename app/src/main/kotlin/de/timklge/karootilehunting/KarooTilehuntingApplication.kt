package de.timklge.karootilehunting

import android.app.Application
import de.timklge.karootilehunting.services.ClusterDrawService
import de.timklge.karootilehunting.services.ExploreTilesService
import de.timklge.karootilehunting.services.KarooSystemServiceProvider
import de.timklge.karootilehunting.services.StatshuntersTilesProvider
import de.timklge.karootilehunting.services.TileDownloadService
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::KarooSystemServiceProvider)
    singleOf(::StatshuntersTilesProvider)
    singleOf(::ClusterDrawService)
    singleOf(::TileDownloadService)
    singleOf(::ExploreTilesService)
}

class KarooTilehuntingAppliation : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KarooTilehuntingAppliation)
            modules(appModule)
        }
    }
}