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