package com.tvstreamnode.tv.data.repository

import com.tvstreamnode.tv.data.api.TvheadendApi
import com.tvstreamnode.tv.data.model.Channel

class ChannelRepository(
    private val api: TvheadendApi
) {

    suspend fun getChannels(): Result<List<Channel>> {
        return try {
            val response = api.getChannels()
            val channels = response.entries.map { entry ->
                Channel(
                    uuid = entry.uuid,
                    name = entry.name,
                    number = entry.number,
                    icon = entry.icon
                )
            }
            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
