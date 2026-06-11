package com.tvstreamnode.tv

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.tvstreamnode.tv.data.api.RetrofitClient
import com.tvstreamnode.tv.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : androidx.fragment.app.FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SettingsFragment(), android.R.id.content)
        }
    }

    class SettingsFragment : GuidedStepSupportFragment() {

        private val prefs by lazy { Preferences(requireContext()) }

        private lateinit var urlAction: GuidedAction
        private lateinit var usernameAction: GuidedAction
        private lateinit var passwordAction: GuidedAction
        private lateinit var testAction: GuidedAction

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                getString(R.string.settings_title),
                getString(R.string.server_url_summary),
                null,
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            urlAction = GuidedAction.Builder(requireContext())
                .id(ACTION_URL)
                .title(getString(R.string.server_url))
                .description(prefs.serverUrl.ifBlank { getString(R.string.server_url_summary) })
                .editInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                .editable(true)
                .build()

            usernameAction = GuidedAction.Builder(requireContext())
                .id(ACTION_USERNAME)
                .title(getString(R.string.username))
                .description(prefs.username.ifBlank { "—" })
                .editInputType(InputType.TYPE_CLASS_TEXT)
                .editable(true)
                .build()

            passwordAction = GuidedAction.Builder(requireContext())
                .id(ACTION_PASSWORD)
                .title(getString(R.string.password))
                .description(if (prefs.password.isNotBlank()) "••••••••" else "—")
                .editInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .editable(true)
                .build()

            testAction = GuidedAction.Builder(requireContext())
                .id(ACTION_TEST)
                .title(getString(R.string.test_connection))
                .build()

            actions.add(urlAction)
            actions.add(usernameAction)
            actions.add(passwordAction)
            actions.add(testAction)
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            if (action.id == ACTION_TEST) {
                saveFields()
                testConnection()
            }
        }

        override fun onGuidedActionEdited(action: GuidedAction) {
            when (action.id) {
                ACTION_URL -> {
                    urlAction.description = action.editDescription.ifBlank { getString(R.string.server_url_summary) }
                }
                ACTION_USERNAME -> {
                    usernameAction.description = action.editDescription.ifBlank { "—" }
                }
                ACTION_PASSWORD -> {
                    passwordAction.description = if (action.editDescription.isNotBlank()) "••••••••" else "—"
                }
            }
        }

        override fun onPause() {
            super.onPause()
            saveFields()
        }

        private fun saveFields() {
            prefs.serverUrl = urlAction.editDescription.trim()
            prefs.username = usernameAction.editDescription.trim()
            prefs.password = passwordAction.editDescription
        }

        private fun testConnection() {
            val url = prefs.serverUrl
            if (url.isBlank()) {
                showToast(getString(R.string.connection_failed))
                return
            }

            testAction.title = getString(R.string.connecting)
            testAction.isEnabled = false
            notifyActionChanged(testAction.id)

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val api = RetrofitClient.getApi(prefs)
                            api.testConnection()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                    showToast(if (result) getString(R.string.connection_ok) else getString(R.string.connection_failed))
                } finally {
                    testAction.title = getString(R.string.test_connection)
                    testAction.isEnabled = true
                    notifyActionChanged(testAction.id)
                }
            }
        }

        private fun showToast(message: String) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        companion object {
            private const val ACTION_URL = 1L
            private const val ACTION_USERNAME = 2L
            private const val ACTION_PASSWORD = 3L
            private const val ACTION_TEST = 4L
        }
    }
}
