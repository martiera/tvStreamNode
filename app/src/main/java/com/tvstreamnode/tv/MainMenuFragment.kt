package com.tvstreamnode.tv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import com.tvstreamnode.tv.util.Preferences
import androidx.recyclerview.widget.RecyclerView

class MainMenuFragment : Fragment() {

    companion object {
        private val MENU_ITEMS = arrayOf("Channels", "Lists", "Settings", "Exit")
        private const val MENU_WIDTH_FRACTION = 0.25f
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val menuW = (resources.displayMetrics.widthPixels * MENU_WIDTH_FRACTION).toInt()

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0D1B2A"))
        }

        // Right side (dark, fills rest)
        val rightBg = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(rightBg)

        // Left menu panel
        val menuPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(menuW, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#1A73E8"))
        }

        // Title label at top
        menuPanel.addView(TextView(requireContext()).apply {
            text = "TvStreamNode"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 48, 16, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        // Menu items
        val grid = VerticalGridView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setNumColumns(1)
            setPadding(16, 8, 16, 16)
        }

        grid.adapter = object : RecyclerView.Adapter<MenuViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
                val tv = TextView(requireContext()).apply {
                    textSize = 22f
                    setPadding(24, 24, 24, 24)
                    isFocusable = true
                    isClickable = true
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    updateBackground(this, false)
                }
                return MenuViewHolder(tv)
            }

            override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
                val tv = holder.textView
                tv.text = MENU_ITEMS[position]
                updateBackground(tv, tv.isFocused)

                tv.onFocusChangeListener = null
                tv.setOnFocusChangeListener { v, hasFocus ->
                    updateBackground(v as TextView, hasFocus)
                }

                tv.setOnClickListener {
                    when (position) {
                        0 -> {
                            val p = Preferences(requireContext())
                            parentFragmentManager.beginTransaction()
                                .replace(android.R.id.content, ChannelBrowseFragment().apply {
                                    arguments = Bundle().apply {
                                        putString("select_uuid", p.lastChannelUuid)
                                    }
                                })
                                .addToBackStack("browse")
                                .commit()
                        }
                        1 -> {
                            parentFragmentManager.beginTransaction()
                                .replace(android.R.id.content, ListsFragment())
                                .addToBackStack("lists")
                                .commit()
                        }
                        2 -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                        3 -> requireActivity().finishAffinity()
                    }
                }
            }

            override fun getItemCount() = MENU_ITEMS.size
        }

        menuPanel.addView(grid)
        root.addView(menuPanel)

        return root
    }

    private fun updateBackground(tv: TextView, focused: Boolean) {
        val bg = GradientDrawable().apply {
            cornerRadius = 10f
            if (focused) {
                setColor(Color.WHITE)
            } else {
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }
        tv.background = bg
        tv.setTextColor(if (focused) Color.parseColor("#1A73E8") else Color.WHITE)
    }

    private class MenuViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}
