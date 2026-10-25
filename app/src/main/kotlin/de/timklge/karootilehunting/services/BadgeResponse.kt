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

package de.timklge.karootilehunting.services

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class BadgeResponse(
    val badges: List<Badge>
)

@Serializable
data class Badge(
    val id: Int,
    val name: String,
    @SerialName("badgetype_id") val badgeTypeId: Int,
    val info: String,
    val bootstrap: Int,
    val level: Int,
    val icon: String,
    val sort: Int,
    val coor: JsonElement,
    val country: JsonElement,
    val badgetype: BadgeType,
    val badgefilter: List<BadgeFilter>
)

@Serializable
data class BadgeType(
    val id: Int,
    val name: String,
    val action: String,
    val icon: String,
    val info: String,
    @SerialName("asGroup") val asGroup: Int,
    val maxlevel: Int
)

@Serializable
data class BadgeFilter(
    val id: Int,
    @SerialName("parent_id") val parentId: Int,
    val name: String,
    val pivot: Pivot
)

@Serializable
data class Pivot(
    @SerialName("badge_id") val badgeId: Int,
    @SerialName("badgefilter_id") val badgeFilterId: Int
)