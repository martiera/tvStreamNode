package com.tvstreamnode.tv.ui

import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.tvstreamnode.tv.R
import com.tvstreamnode.tv.data.model.Channel

class ChannelCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isClickable = true
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val cardView = viewHolder.view as ImageCardView

        val title = buildString {
            channel.number?.let { append("$it  ") }
            channel.name?.let { append(it) }
        }

        cardView.titleText = title
        cardView.setMainImageDimensions(200, 150)
        cardView.setDefaultImage(
            ResourcesCompat.getDrawable(
                cardView.resources,
                R.drawable.ic_channel,
                null
            )
        )
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.titleText = null
        cardView.contentText = null
    }
}
