/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karootilehunting

import android.app.Application
import de.timklge.karootilehunting.services.ClusterDrawService
import de.timklge.karootilehunting.services.ExploreTilesService
import de.timklge.karootilehunting.services.KarooSystemServiceProvider
import de.timklge.karootilehunting.services.StatshuntersBadgesProvider
import de.timklge.karootilehunting.services.StatshuntersTilesProvider
import de.timklge.karootilehunting.services.TileDownloadService
import de.timklge.karootilehunting.services.BadgeDrawService
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
    singleOf(::StatshuntersBadgesProvider)
    singleOf(::BadgeDrawService)
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