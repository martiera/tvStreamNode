package com.tvstreamnode.tv

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.tvstreamnode.tv.data.api.RetrofitClient
import com.tvstreamnode.tv.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private var autoPlayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Preferences(this)

        if (!prefs.isConfigured) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            showMenu()

            if (prefs.hasLastChannel) {
                lifecycleScope.launch {
                    val connected = withContext(Dispatchers.IO) {
                        try {
                            RetrofitClient.reset()
                            RetrofitClient.getApi(prefs).testConnection()
                            true
                        } catch (_: Exception) { false }
                    }
                    if (connected) {
                        autoPlayed = true
                        startActivity(Intent(this@MainActivity, PlaybackActivity::class.java).apply {
                            putExtra("channel_uuid", prefs.lastChannelUuid)
                            putExtra("channel_name", prefs.lastChannelName)
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (autoPlayed) {
            autoPlayed = false
            val prefs = Preferences(this)
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ChannelBrowseFragment().apply {
                    arguments = Bundle().apply {
                        putString("select_uuid", prefs.lastChannelUuid)
                    }
                })
                .addToBackStack("epg")
                .commit()
        }
    }

    private fun showMenu() {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, MainMenuFragment())
            .commit()
    }
}
