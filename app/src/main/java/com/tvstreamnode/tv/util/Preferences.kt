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

    var streamType: String
        get() = prefs.getString(KEY_STREAM_TYPE, "auto") ?: "auto"
        set(value) = prefs.edit { putString(KEY_STREAM_TYPE, value) }

    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUBTITLE_LANGUAGE, "channel") ?: "channel"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_LANGUAGE, value) }

    var streamProfile: String
        get() = prefs.getString(KEY_STREAM_PROFILE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_STREAM_PROFILE, value) }

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
        private const val KEY_STREAM_TYPE = "stream_type"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_STREAM_PROFILE = "stream_profile"
    }
}
