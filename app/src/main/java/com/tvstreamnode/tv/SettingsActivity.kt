package com.tvstreamnode.tv

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
            val connectionError = requireActivity().intent?.getBooleanExtra("connection_error", false) ?: false
            val description = if (connectionError) {
                "⚠ Connection failed — check your server settings"
            } else {
                getString(R.string.server_url_summary)
            }
            return GuidanceStylist.Guidance(
                getString(R.string.settings_title),
                description,
                null,
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            urlAction = GuidedAction.Builder(requireContext())
                .id(ACTION_URL)
                .title(getString(R.string.server_url))
                .description(prefs.serverUrl.ifBlank { getString(R.string.server_url_summary) })
                .build()

            usernameAction = GuidedAction.Builder(requireContext())
                .id(ACTION_USERNAME)
                .title(getString(R.string.username))
                .description(prefs.username.ifBlank { "—" })
                .build()

            passwordAction = GuidedAction.Builder(requireContext())
                .id(ACTION_PASSWORD)
                .title(getString(R.string.password))
                .description(if (prefs.password.isNotBlank()) "••••••••" else "—")
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
            when (action.id) {
                ACTION_URL -> showEditDialog(
                    getString(R.string.server_url),
                    prefs.serverUrl,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                ) { value ->
                    prefs.serverUrl = value
                    urlAction.description = value.ifBlank { getString(R.string.server_url_summary) }
                    notifyActionChanged(action.id.toInt())
                }
                ACTION_USERNAME -> showEditDialog(
                    getString(R.string.username),
                    prefs.username,
                    InputType.TYPE_CLASS_TEXT
                ) { value ->
                    prefs.username = value
                    usernameAction.description = value.ifBlank { "—" }
                    notifyActionChanged(action.id.toInt())
                }
                ACTION_PASSWORD -> showEditDialog(
                    getString(R.string.password),
                    prefs.password,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                ) { value ->
                    prefs.password = value
                    passwordAction.description = if (value.isNotBlank()) "••••••••" else "—"
                    notifyActionChanged(action.id.toInt())
                }
                ACTION_TEST -> {
                    RetrofitClient.reset()
                    testConnection()
                }
            }
        }

        private fun showEditDialog(title: String, currentValue: String, inputType: Int, onSave: (String) -> Unit) {
            val input = EditText(requireContext()).apply {
                setText(currentValue)
                setSelection(currentValue.length)
                this.inputType = inputType
                imeOptions = EditorInfo.IME_ACTION_DONE
                isSingleLine = true
            }

            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onSave(input.text.toString())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun testConnection() {
            val url = prefs.serverUrl
            if (url.isBlank()) {
                showToast(getString(R.string.connection_failed))
                return
            }

            testAction.title = getString(R.string.connecting)
            testAction.isEnabled = false
            notifyActionChanged(testAction.id.toInt())

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val api = RetrofitClient.getApi(prefs)
                            api.testConnection()
                            null
                        } catch (e: Exception) {
                            e.message ?: "Unknown error"
                        }
                    }
                    if (result == null) {
                        showToast(getString(R.string.connection_ok))
                    } else {
                        showToast("$result")
                    }
                } finally {
                    testAction.title = getString(R.string.test_connection)
                    testAction.isEnabled = true
                    notifyActionChanged(testAction.id.toInt())
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
