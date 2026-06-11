package com.tvstreamnode.tv.data.model

import com.google.gson.annotations.SerializedName

data class EpgEvent(
    @SerializedName("eventId") val eventId: Long,
    @SerializedName("channelUuid") val channelUuid: String,
    @SerializedName("channelName") val channelName: String?,
    @SerializedName("channelNumber") val channelNumber: String?,
    @SerializedName("channelIcon") val channelIcon: String?,
    @SerializedName("start") val start: Long,
    @SerializedName("stop") val stop: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("subtitle") val subtitle: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("seasonNumber") val seasonNumber: Int?,
    @SerializedName("episodeNumber") val episodeNumber: Int?,
    @SerializedName("episodeOnscreen") val episodeOnscreen: String?,
    @SerializedName("starRating") val starRating: Int?,
    @SerializedName("ageRating") val ageRating: Int?,
    @SerializedName("genre") val genre: List<Int>?,
    @SerializedName("nextEventId") val nextEventId: Long?
)
