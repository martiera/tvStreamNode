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
                .replace(android.R.id.content, MainMenuFragment())
                .commit()
        }
    }
}
