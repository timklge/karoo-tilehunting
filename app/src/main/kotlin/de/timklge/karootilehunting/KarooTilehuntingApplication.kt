package de.timklge.karootilehunting

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::KarooSystemServiceProvider)
    singleOf(::TilehuntingViewModelProvider)
    singleOf(::StatshuntersTilesProvider)
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