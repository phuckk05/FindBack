package com.app.findback.ui.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.R
import com.app.findback.databinding.ItemMessageLocationBinding
import com.app.findback.databinding.ItemMessagePostBinding
import com.app.findback.databinding.ItemMessageReceivedBinding
import com.app.findback.databinding.ItemMessageSentBinding
import com.app.findback.domain.models.Message
import com.app.findback.domain.models.MessageType
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val currentUserId: String,
    private val onLocationClick: (Double, Double) -> Unit,
    private val onPostClick: (String) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TEXT_SENT = 0
        private const val VIEW_TEXT_RECEIVED = 1
        private const val VIEW_LOC_SENT = 2
        private const val VIEW_LOC_RECEIVED = 3
        private const val VIEW_POST_SENT = 4
        private const val VIEW_POST_RECEIVED = 5

        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(old: Message, new: Message) = old.messageId == new.messageId
            override fun areContentsTheSame(old: Message, new: Message) = old == new
        }
    }

    private val myUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid?.trim() ?: currentUserId.trim()

    private fun isMine(senderId: String): Boolean {
        if (senderId.isBlank()) return false
        return myUid == senderId.trim()
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        val isSent = isMine(msg.senderId)
        return when (msg.type) {
            MessageType.TEXT     -> if (isSent) VIEW_TEXT_SENT else VIEW_TEXT_RECEIVED
            MessageType.LOCATION -> if (isSent) VIEW_LOC_SENT else VIEW_LOC_RECEIVED
            MessageType.POST     -> if (isSent) VIEW_POST_SENT else VIEW_POST_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TEXT_SENT     -> TextSentVH(ItemMessageSentBinding.inflate(inflater, parent, false))
            VIEW_TEXT_RECEIVED -> TextReceivedVH(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            VIEW_LOC_SENT      -> LocationVH(ItemMessageLocationBinding.inflate(inflater, parent, false), true)
            VIEW_LOC_RECEIVED  -> LocationVH(ItemMessageLocationBinding.inflate(inflater, parent, false), false)
            VIEW_POST_SENT     -> PostVH(ItemMessagePostBinding.inflate(inflater, parent, false), true)
            else               -> PostVH(ItemMessagePostBinding.inflate(inflater, parent, false), false)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is TextSentVH     -> holder.bind(msg)
            is TextReceivedVH -> holder.bind(msg)
            is LocationVH     -> holder.bind(msg)
            is PostVH         -> holder.bind(msg)
        }
    }

    inner class TextSentVH(private val b: ItemMessageSentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessage.text = msg.content
            b.tvTime.text = msg.timestamp.toTimeString()
        }
    }

    inner class TextReceivedVH(private val b: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessage.text = msg.content
            b.tvTime.text = msg.timestamp.toTimeString()
        }
    }

    inner class LocationVH(
        private val b: ItemMessageLocationBinding,
        private val isSent: Boolean
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(msg: Message) {
            val loc = msg.location ?: return
            b.tvAddress.text = loc.address.ifEmpty { "Xem trên bản đồ" }
            b.tvTime.text = msg.timestamp.toTimeString()
            (b.root as? LinearLayout)?.gravity = if (isSent) Gravity.END else Gravity.START

            val context = b.mapView.context
            val geo = GeoPoint(loc.latitude, loc.longitude)

            // Set User-Agent để OSM không block
            Configuration.getInstance().userAgentValue = context.packageName

            b.mapView.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)   // tắt zoom/pan trong chat
                isClickable = false
                isFocusable = false
                controller.setZoom(15.0)
                controller.setCenter(geo)
                overlays.clear()
                invalidate()
            }

            // Click toàn bộ card → mở Google Maps
            b.mapClickOverlay.setOnClickListener { onLocationClick(loc.latitude, loc.longitude) }
            b.cardLocation.setOnClickListener { onLocationClick(loc.latitude, loc.longitude) }
        }
    }

    inner class PostVH(
        private val b: ItemMessagePostBinding,
        private val isSent: Boolean
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            val post = msg.post ?: return

            b.tvPostTitle.text = post.title
            b.tvPostDesc.text = post.description.take(80) + if (post.description.length > 80) "..." else ""
            b.tvTime.text = msg.timestamp.toTimeString()
            b.tvSentLabel.text = if (isSent) "Bạn đã chia sẻ" else "Đã chia sẻ bài viết"

            (b.root as? LinearLayout)?.gravity = if (isSent) Gravity.END else Gravity.START

            Glide.with(b.ivPostImage.context)
                .load(post.imageUrl.ifEmpty { null })
                .apply(RequestOptions().error(R.drawable.ic_post).placeholder(R.drawable.ic_post))
                .into(b.ivPostImage)

            b.cardPost.setOnClickListener { onPostClick(post.postId) }
        }
    }

    private fun Long.toTimeString(): String {
        if (this == 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
    }
}