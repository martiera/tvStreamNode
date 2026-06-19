package com.tvstreamnode.tv.data.model

data class ChannelList(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val channelIds: List<String> = emptyList()
)
