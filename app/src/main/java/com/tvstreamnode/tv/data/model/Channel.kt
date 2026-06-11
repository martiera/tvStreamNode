package com.tvstreamnode.tv.data.model

import com.google.gson.annotations.SerializedName

data class Channel(
    val uuid: String,
    val name: String?,
    val number: String?,
    val icon: String?
)
