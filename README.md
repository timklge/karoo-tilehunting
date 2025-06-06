# Karoo Tilehunting Extension 

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/timklge/karoo-tilehunting/android.yml)](https://github.com/timklge/karoo-tilehunting/actions/workflows/android.yml)
[![GitHub Downloads (specific asset, all releases)](https://img.shields.io/github/downloads/timklge/karoo-tilehunting/app-release.apk)](https://github.com/timklge/karoo-tilehunting/releases)
[![GitHub License](https://img.shields.io/github/license/timklge/karoo-tilehunting)](https://github.com/timklge/karoo-tilehunting/blob/master/LICENSE)

This extension for Karoo devices adds a map overlay that shows explored tiles and an alert if you ride into an unexplored one. Optionally, past activities are also displayed on the map. Tiles and activities are fetched from [statshunters.com](https://www.statshunters.com).
You can read more about tilehunting in the [StatsHunters FAQ](https://www.statshunters.com/faq-10-what-are-explorer-tiles) or watch the [nice introduction video](https://www.youtube.com/watch?v=e63UKfvPw9g) for an overview on StatsHunters' features.

> [!NOTE]
> Please note that this extension is not affiliated with Statshunters. If you like it, consider [supporting Statshunters](https://statshunters.com/donate)!

Compatible with Karoo 2 and Karoo 3 devices.

![Menu](menu.png)
![Tiles](tiles.png)
![Badges](badges.png)
![Border](border.png)

## Installation

This extension is available as part of the extension library on your Karoo device. More information is available in the [Hammerhead FAQ](https://support.hammerhead.io/hc/en-us/articles/34676015530907-Karoo-OS-Extensions-Library).

## Usage

After installing this app on your Karoo, open the app from the extensions menu. Tap on the
"Connnect StatsHunters" button to connect your account. You will need to open the
[StatsHunters share page](https://www.statshunters.com/share) on your phone or computer, create
a sharing link that includes your heatmap with tiles and activities enabled and type
in the sharing code of your new link on the Karoo. Optionally, enable "Badges" to fetch badges.

After saving your code, the app will try to download the activities and your explored tiles.
Make sure you are connected to WiFi for this step. The app will then draw the outlines of your explored and unexplored tiles
onto the map when you start a ride.

Please note that currently, only tiles close to your current location are drawn,
regardless of where you pan on the map if you unlock it. 

The following colors are used:

- Blue: Your biggest continuous explored square of tiles
- Green: A tile whose neighbors are all explored
- Red: A tile that is explored, but has unexplored neighbors
- Gray: An unexplored tile
- Bright Green / Lime: A tile you have just explored

If you ride into a new tile, the app will show an alert and draw the tile in bright green.
You can reset the bright green colored tiles by tapping the "Reset Tiles" button in the app's menu.

If you enable "Badges" sharing on your sharing link, the app will fetch your achieved badges and show them in the app's menu. You can initiate navigation to one of the badge locations by tapping on the badge in the menu. The app will also add the badge locations as POIs on the Karoo map.

To view past rides, enable the respective setting in the app's main menu. The app will then show your past rides on the map as light gray lines.

You can temporarily hide the tile outlines on the map by tapping the stack icon in the same way you can hide the native heatmap.

Also, you can add the count of explored tiles and count of recently explored tiles to your data pages as data fields.

## Credits

- Icons are from [boxicons.com](https://boxicons.com) ([MIT-licensed](icon_credits.txt))
- Interfaces with [statshunters.com](https://www.statshunters.com)
- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache2-licensed)
- Alert sound played over bluetooth is Alert6 by RICHERlandTV - https://freesound.org/s/351545/ (CC-BY 4.0)
