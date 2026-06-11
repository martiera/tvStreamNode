package com.tvstreamnode.tv.data.model

import com.google.gson.annotations.SerializedName

data class ChannelGridResponse(
    @SerializedName("entries") val entries: List<ChannelGridEntry>,
    @SerializedName("totalCount") val totalCount: Int?
)

data class ChannelGridEntry(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String?,
    @SerializedName("number") val number: String?,
    @SerializedName("icon") val icon: String?,
    @SerializedName("tags") val tags: List<String>?
)

data class EpgGridResponse(
    @SerializedName("entries") val entries: List<EpgEvent>,
    @SerializedName("totalCount") val totalCount: Int
)
