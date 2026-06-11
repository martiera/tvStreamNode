package com.tvstreamnode.tv.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Preferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tvstreamnode", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PASSWORD, value) }

    var lastChannelUuid: String
        get() = prefs.getString(KEY_LAST_CHANNEL_UUID, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LAST_CHANNEL_UUID, value) }

    var lastChannelName: String
        get() = prefs.getString(KEY_LAST_CHANNEL_NAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LAST_CHANNEL_NAME, value) }

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank()

    val hasLastChannel: Boolean
        get() = lastChannelUuid.isNotBlank()

    fun clear() {
        prefs.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_CHANNEL_UUID = "last_channel_uuid"
        private const val KEY_LAST_CHANNEL_NAME = "last_channel_name"
    }
}
