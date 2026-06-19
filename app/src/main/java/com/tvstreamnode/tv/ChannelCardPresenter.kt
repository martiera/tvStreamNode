package com.tvstreamnode.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.tvstreamnode.tv.data.model.Channel
import com.tvstreamnode.tv.data.model.EpgEvent
import com.tvstreamnode.tv.util.ListManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChannelCardPresenter : Presenter() {

    var onClickListener: ((Channel, EpgEvent?) -> Unit)? = null
    var onDownListener: ((Channel) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val density = parent.resources.displayMetrics.density
        val cardW = (200 * density).toInt()
        val cardH = (230 * density).toInt()

        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cardW, cardH)
            setPadding(12, 10, 12, 10)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#333333"))
            }
            background = bg
            isFocusable = true
            isClickable = true
        }

        // Channel name
        val channelName = TextView(parent.context).apply {
            id = 1
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(channelName)

        // Separator
        card.addView(TextView(parent.context).apply {
            textSize = 8f
            text = "─".repeat(30)
            setTextColor(Color.parseColor("#444444"))
            maxLines = 1
        })

        // Show title
        val showTitle = TextView(parent.context).apply {
            id = 2
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(showTitle)

        // Progress bar container
        val progressRow = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (6 * density).toInt()
            ).apply { topMargin = (4 * density).toInt(); bottomMargin = (4 * density).toInt() }
        }

        val barBg = View(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }
        progressRow.addView(barBg)

        val barFill = View(parent.context).apply {
            id = 3
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1A73E8"))
                cornerRadius = 3f
            }
            background = bg
        }
        progressRow.addView(barFill)
        card.addView(progressRow)

        // Time text
        val timeText = TextView(parent.context).apply {
            id = 4
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            maxLines = 1
        }
        card.addView(timeText)

        // Description
        val descText = TextView(parent.context).apply {
            id = 5
            textSize = 14f
            setTextColor(Color.parseColor("#777777"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(descText)

        // Spacer pushes list tags to bottom
        card.addView(View(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })

        // Thin separator above list tags
        card.addView(View(parent.context).apply {
            setBackgroundColor(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()
            ).apply {
                topMargin = (6 * density).toInt()
                bottomMargin = (4 * density).toInt()
                leftMargin = (4 * density).toInt()
                rightMargin = (4 * density).toInt()
            }
        })

        // List tags
        val listTags = TextView(parent.context).apply {
            id = 6
            tag = "list_tags"
            textSize = 10f
            setTextColor(Color.parseColor("#BBBBBB"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(listTags)

        // Focus highlight
        val focusBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2A2A2A"))
            cornerRadius = 8f
            setStroke(2, Color.parseColor("#1A73E8"))
        }
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 8f
            setStroke(1, Color.parseColor("#333333"))
        }

        card.onFocusChangeListener = null
        card.setOnFocusChangeListener { v, hasFocus ->
            val c = v as LinearLayout
            c.background = if (hasFocus) focusBg else normalBg
            c.getChildAt(0)?.let {
                if (it is TextView) it.setTextColor(if (hasFocus) Color.parseColor("#1A73E8") else Color.WHITE)
            }
        }

        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val pair = item as Pair<Channel, EpgEvent?>
        val channel = pair.first
        val event = pair.second

        val card = viewHolder.view as LinearLayout

        val channelName = card.getChildAt(0) as TextView
        val showTitle = card.getChildAt(2) as TextView
        val barFill = (card.getChildAt(3) as FrameLayout).getChildAt(1) as View
        val timeText = card.getChildAt(4) as TextView
        val descText = card.getChildAt(5) as TextView

        channelName.text = channel.name ?: ""

        if (event != null) {
            showTitle.text = event.title ?: "—"
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeText.text = "${sdf.format(Date(event.start * 1000))}  →  ${sdf.format(Date(event.stop * 1000))}"
            descText.text = event.description ?: event.subtitle ?: ""
            descText.visibility = if (descText.text.isNullOrBlank()) ViewGroup.GONE else ViewGroup.VISIBLE

            val now = System.currentTimeMillis() / 1000
            if (now >= event.start && now < event.stop) {
                val pct = ((now - event.start).toFloat() / (event.stop - event.start) * 100).coerceIn(0f, 100f)
                barFill.post {
                    val parentW = (barFill.parent as View).width
                    val lp = barFill.layoutParams
                    if (lp is FrameLayout.LayoutParams) lp.width = (parentW * pct / 100).toInt().coerceAtLeast(0)
                    barFill.layoutParams = lp
                }
            } else {
                barFill.post {
                    val lp = barFill.layoutParams
                    if (lp is FrameLayout.LayoutParams) lp.width = 0
                    barFill.layoutParams = lp
                }
            }
        } else {
            showTitle.text = "No program data"
            timeText.text = ""
            descText.text = ""
            descText.visibility = ViewGroup.GONE
            barFill.post {
                val lp = barFill.layoutParams
                if (lp is FrameLayout.LayoutParams) lp.width = 0
                barFill.layoutParams = lp
            }
        }

        // List tags
        ListManager.init(card.context)
        val listTags = card.findViewWithTag("list_tags") as? TextView
        listTags?.let { tv ->
            val names = ListManager.getListNamesForChannel(channel.uuid)
            tv.text = if (names.isNotEmpty()) names.joinToString(" · ") else ""
            tv.visibility = if (names.isNotEmpty()) ViewGroup.VISIBLE else ViewGroup.GONE
        }

        // Click handler
        card.setOnClickListener {
            onClickListener?.invoke(channel, event)
        }

        // Down handler — show list menu
        card.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                onDownListener?.invoke(channel)
                true
            } else false
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // nothing needed
    }
}
