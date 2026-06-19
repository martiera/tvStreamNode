package com.tvstreamnode.tv

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tvstreamnode.tv.util.ListManager

class ListsFragment : Fragment() {

    private lateinit var listsContainer: LinearLayout

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ListManager.init(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val header = TextView(requireContext()).apply {
            text = "‹  My Lists"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        root.addView(header)

        // Create button
        val createNormalBg = GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")) }
        val createBtn = TextView(requireContext()).apply {
            text = "+  Create New List"
            textSize = 16f
            setTextColor(Color.parseColor("#1A73E8"))
            gravity = Gravity.CENTER
            setPadding(24, 20, 24, 20)
            isFocusable = true
            isClickable = true
            background = createNormalBg
            setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A2A"))
                    setStroke(2, Color.parseColor("#1A73E8"))
                } else createNormalBg
            }
            setOnClickListener {
                val input = EditText(requireContext()).apply {
                    hint = "List name"
                    setTextColor(Color.WHITE)
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("New List")
                    .setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            ListManager.create(name)
                            refreshView()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        root.addView(createBtn)

        // Lists container
        listsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(listsContainer)

        refreshView()


        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                parentFragmentManager.popBackStack()
                true
            } else false
        }

        return root
    }

    private fun refreshView() {
        if (!::listsContainer.isInitialized) return
        listsContainer.removeAllViews()
        val lists = ListManager.getAll()

        for (list in lists) {
            val rowNormalBg = GradientDrawable().apply { setColor(Color.parseColor("#1A1A1A")) }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * resources.displayMetrics.density).toInt())
                setPadding(24, 0, 24, 0)
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                background = rowNormalBg
                setOnFocusChangeListener { v, hasFocus ->
                    v.background = if (hasFocus) GradientDrawable().apply {
                        setColor(Color.parseColor("#2A2A2A"))
                        setStroke(2, Color.parseColor("#1A73E8"))
                    } else rowNormalBg
                }
            }

            row.addView(TextView(requireContext()).apply {
                text = list.name
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(requireContext()).apply {
                text = "${list.channelIds.size} channels"
                textSize = 14f
                setTextColor(Color.parseColor("#999999"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 24 }
            })

            val deleteBtn = TextView(requireContext()).apply {
                text = "✕"
                textSize = 18f
                setTextColor(Color.parseColor("#FF5252"))
                setPadding(16, 8, 16, 8)
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete \"${list.name}\"?")
                        .setMessage("Channels in this list will not be affected.")
                        .setPositiveButton("Delete") { _, _ ->
                            ListManager.delete(list.id)
                            refreshView()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            row.addView(deleteBtn)

            row.setOnClickListener {
                val ft = parentFragmentManager.beginTransaction()
                ft.replace(android.R.id.content, ChannelBrowseFragment().apply {
                    arguments = Bundle().apply {
                        putString("list_id", list.id)
                        putString("list_name", list.name)
                    }
                })
                ft.addToBackStack("browse")
                ft.commit()
            }

            listsContainer.addView(row)
        }
    }
}
