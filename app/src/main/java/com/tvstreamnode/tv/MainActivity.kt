package com.tvstreamnode.tv

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import com.tvstreamnode.tv.util.Preferences

class MainActivity : androidx.fragment.app.FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Preferences(this)

        if (!prefs.isConfigured) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, BrowseFragment())
                .commit()
        }
    }
}
