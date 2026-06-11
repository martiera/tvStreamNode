package com.tvstreamnode.tv.ui

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.tvstreamnode.tv.data.model.EpgEvent

class EpgCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isClickable = true
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val event = item as EpgEvent
        val cardView = viewHolder.view as ImageCardView

        val title = event.title ?: "—"
        val time = formatTime(event.start, event.stop)

        cardView.titleText = title
        cardView.contentText = time
        cardView.setMainImageDimensions(200, 150)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.titleText = null
        cardView.contentText = null
    }

    private fun formatTime(start: Long, stop: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return "${sdf.format(java.util.Date(start * 1000))} - ${sdf.format(java.util.Date(stop * 1000))}"
    }
}
