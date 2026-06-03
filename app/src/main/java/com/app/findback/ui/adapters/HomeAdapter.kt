package com.app.findback.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.R
import com.app.findback.data.source.remote.FirebaseMessageDataSource
import com.app.findback.databinding.ItemPostBinding
import com.app.findback.domain.models.Comment
import com.app.findback.domain.models.Notification
import com.app.findback.domain.models.Post
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeAdapter(
    private val context: Context,
    private val list: MutableList<Post>
) : RecyclerView.Adapter<HomeAdapter.MyViewHolder>() {

    private val commentCache = mutableMapOf<String, List<Comment>>()
    private val commentListeners = mutableMapOf<String, ValueEventListener>()
    private val activeHolders = mutableMapOf<String, MyViewHolder>()
    private val expandedPosts = mutableSetOf<String>()

    private val db = FirebaseDatabase.getInstance()
    private var onItemClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(position: Int)
        fun onItemClickShare(position: Int)
        fun onItemClickChat(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.onItemClickListener = listener
    }

    inner class MyViewHolder(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        var commentSection: View? = null
        var boundPostId: String = ""
        var pos: Int = 0

        init {
            binding.root.setOnClickListener { onItemClickListener?.onItemClick(pos) }
            binding.btnShare.setOnClickListener { onItemClickListener?.onItemClickShare(pos) }
            binding.btnChat.setOnClickListener { onItemClickListener?.onItemClickChat(pos) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(context), parent, false)
        return MyViewHolder(binding)
    }

    override fun getItemCount() = list.size
    override fun getItemId(position: Int): Long = list[position].postId.hashCode().toLong()

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.pos = position
        val post = list[position]

        if (holder.boundPostId.isNotEmpty() && holder.boundPostId != post.postId) {
            activeHolders.remove(holder.boundPostId)
            holder.binding.commentContainer.removeAllViews()
            holder.commentSection = null
        }

        holder.boundPostId = post.postId
        activeHolders[post.postId] = holder

        with(holder.binding) {
            tvTitle.text = post.title
            tvDescription.text = post.description
            tvLocation.text = post.locationText.ifEmpty { context.getString(R.string.unknown_location) }

            tvName.text = context.getString(R.string.loading)
            imgAvatar.setImageResource(R.drawable.logo_tran)

            if (post.userId.isNotEmpty()) {
                db.getReference("users").child(post.userId).get()
                    .addOnSuccessListener { snapshot ->
                        if (holder.boundPostId != post.postId) return@addOnSuccessListener
                        val fullName = snapshot.child("fullName").value?.toString() ?: context.getString(R.string.anonymous)
                        val avatar = snapshot.child("avatar").value?.toString() ?: ""
                        tvName.text = fullName
                        if (avatar.isNotEmpty()) {
                            Glide.with(context).load(avatar).placeholder(R.drawable.logo_tran).circleCrop().into(imgAvatar)
                        }
                    }
            } else {
                tvName.text = context.getString(R.string.anonymous)
            }

            tvTime.text = getRelativeTime(post.createdAt)

            val isLost = post.postType == "lost"
            tvStatus.text = if (isLost) context.getString(R.string.lost) else context.getString(R.string.found)
            tvStatus.setTextColor(context.getColor(if (isLost) R.color.primary_red else R.color.primary_blue))

            if (post.imageUrls.isNotEmpty()) {
                rvPostImages.visibility = View.VISIBLE
                rvPostImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                rvPostImages.adapter = PostImageAdapter(post.imageUrls)
            } else {
                rvPostImages.visibility = View.GONE
            }

            attachRealtimeListenerIfNeeded(holder, post)
            setupCommentButton(holder, post)
        }
    }

    override fun onViewRecycled(holder: MyViewHolder) {
        super.onViewRecycled(holder)
        if (holder.boundPostId.isNotEmpty()) {
            activeHolders.remove(holder.boundPostId)
        }
    }

    private fun attachRealtimeListenerIfNeeded(holder: MyViewHolder, post: Post) {
        if (commentListeners.containsKey(post.postId)) {
            val cached = commentCache[post.postId] ?: emptyList()
            holder.binding.btnComment.text = buildCommentLabel(cached.size)
            return
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val comments = snapshot.children.mapNotNull { snap ->
                    (snap.value as? Map<String, Any?>)?.let { Comment.fromMap(it) }
                }.sortedBy { it.createdAt }

                commentCache[post.postId] = comments

                val activeHolder = activeHolders[post.postId] ?: return
                activeHolder.binding.btnComment.text = buildCommentLabel(comments.size)

                if (expandedPosts.contains(post.postId)) {
                    refreshCommentList(activeHolder, post.postId, comments)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        db.getReference("comments").child(post.postId).addValueEventListener(listener)
        commentListeners[post.postId] = listener
    }

    private fun refreshCommentList(holder: MyViewHolder, postId: String, comments: List<Comment>) {
        val section = holder.commentSection ?: return
        val rvComments = section.findViewById<RecyclerView>(R.id.rvInlineComments) ?: return
        (rvComments.adapter as? InlineCommentAdapter)?.submitList(comments)
    }

    private fun setupCommentButton(holder: MyViewHolder, post: Post) {
        val cached = commentCache[post.postId] ?: emptyList()
        holder.binding.btnComment.text = buildCommentLabel(cached.size)

        holder.binding.btnComment.setOnClickListener {
            val postId = post.postId
            if (expandedPosts.contains(postId)) {
                collapseComment(holder, postId)
            } else {
                expandComment(holder, post)
            }
        }

        if (expandedPosts.contains(post.postId)) {
            holder.binding.root.post {
                showCommentSection(holder, post)
            }
        } else {
            holder.commentSection?.visibility = View.GONE
        }
    }

    private fun expandComment(holder: MyViewHolder, post: Post) {
        expandedPosts.add(post.postId)
        showCommentSection(holder, post)
    }

    private fun collapseComment(holder: MyViewHolder, postId: String) {
        expandedPosts.remove(postId)
        holder.commentSection?.visibility = View.GONE
        holder.binding.btnComment.text = buildCommentLabel(commentCache[postId]?.size ?: 0)
        holder.binding.root.requestLayout()
    }

    private fun showCommentSection(holder: MyViewHolder, post: Post) {
        val container = holder.binding.commentContainer

        if (holder.commentSection == null) {
            val section = LayoutInflater.from(context)
                .inflate(R.layout.layout_comment_section, container, false)
            container.addView(section)
            holder.commentSection = section
        }

        val section = holder.commentSection!!
        section.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        if (section.findViewById<RecyclerView>(R.id.rvInlineComments)?.adapter == null) {
            setupInlineCommentUI(holder, post, section)
        }

        holder.binding.root.requestLayout()
        holder.binding.root.post {
            holder.binding.root.requestLayout()
            holder.binding.root.invalidate()
        }
    }

    private fun setupInlineCommentUI(holder: MyViewHolder, post: Post, section: View) {
        val rvComments = section.findViewById<RecyclerView>(R.id.rvInlineComments) ?: return
        val etComment = section.findViewById<EditText>(R.id.etInlineComment) ?: return
        val btnSend = section.findViewById<ImageButton>(R.id.btnSendInlineComment) ?: return

        val commentAdapter = InlineCommentAdapter(context) { comment ->
            deleteComment(post.postId, comment)
        }

        rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            isNestedScrollingEnabled = false
        }

        val cached = commentCache[post.postId] ?: emptyList()
        commentAdapter.submitList(cached)

        btnSend.setOnClickListener {
            val content = etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                sendComment(post.postId, content, etComment, btnSend)
            }
        }
    }

    fun expandCommentForPost(postId: String) {

        expandedPosts.add(postId)

        val holder = activeHolders[postId]
        val post = list.firstOrNull { it.postId == postId }

        if (holder != null && post != null) {

            holder.binding.root.post {
                showCommentSection(holder, post)
            }
        } else {

            val position = list.indexOfFirst { it.postId == postId }
            if (position != -1) notifyItemChanged(position)
        }
    }

    fun getCurrentList(): List<Post> = list.toList()

    private fun sendComment(
        postId: String,
        content: String,
        etComment: EditText,
        btnSend: ImageButton
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(context, context.getString(R.string.please_login), Toast.LENGTH_SHORT).show()
            return
        }

        btnSend.isEnabled = false

        db.getReference("users").child(uid).get()
            .addOnSuccessListener { userSnap ->
                val userName = userSnap.child("fullName").value?.toString()
                    ?: userSnap.child("displayName").value?.toString()
                    ?: context.getString(R.string.user)
                val userAvatar = userSnap.child("avatar").value?.toString() ?: ""

                db.getReference("posts").child(postId).get()
                    .addOnSuccessListener { postSnap ->
                        val postOwnerId = postSnap.child("userId").value?.toString() ?: ""
                        val ref = db.getReference("comments").child(postId)
                        val commentId = ref.push().key ?: return@addOnSuccessListener

                        val comment = Comment(
                            commentId = commentId,
                            postId = postId,
                            userId = uid,
                            userName = userName,
                            userAvatar = userAvatar,
                            content = content,
                            createdAt = System.currentTimeMillis()
                        )

                        ref.child(commentId).setValue(comment.toMap())
                            .addOnSuccessListener {
                                etComment.setText("")
                                btnSend.isEnabled = true
                                if (postOwnerId.isNotEmpty() && postOwnerId != uid) {
                                    sendCommentNotification(postOwnerId, postId, userName, userAvatar, content)
                                }
                            }
                            .addOnFailureListener {
                                btnSend.isEnabled = true
                                Toast.makeText(context, context.getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { btnSend.isEnabled = true }
            }
            .addOnFailureListener { btnSend.isEnabled = true }
    }

    private fun sendCommentNotification(
        postOwnerId: String,
        postId: String,
        commenterName: String,
        commenterAvatar: String,
        commentContent: String
    ) {
        val notification = Notification(
            userId = postOwnerId,
            type = "new_comment",
            title = commenterName,
            content = "Đã bình luận: ${commentContent.take(80)}${if (commentContent.length > 80) "..." else ""}",
            senderId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            senderName = commenterName,
            senderAvatar = commenterAvatar,
            postId = postId,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        FirebaseMessageDataSource().sendNotification(notification)
        sendPushCommentNotification(postOwnerId, commenterName, commentContent, postId)
    }

    private fun deleteComment(postId: String, comment: Comment) {
        db.getReference("comments").child(postId).child(comment.commentId).removeValue()
    }

    private fun buildCommentLabel(count: Int): String =
        if (count > 0) context.getString(R.string.comment_count, count)
        else context.getString(R.string.comment)

    fun removeAllListeners() {
        commentListeners.forEach { (postId, listener) ->
            db.getReference("comments").child(postId).removeEventListener(listener)
        }
        commentListeners.clear()
        activeHolders.clear()
    }

    private fun getRelativeTime(createdAt: Long): String {
        if (createdAt <= 0L) return context.getString(R.string.just_posted)
        val diff = System.currentTimeMillis() - createdAt
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)
        return when {
            minutes < 1 -> context.getString(R.string.just_posted)
            minutes < 60 -> "$minutes ${context.getString(R.string.minutes_ago)}"
            hours < 24 -> "$hours ${context.getString(R.string.hours_ago)}"
            days < 7 -> "$days ${context.getString(R.string.days_ago)}"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))
        }
    }

    private fun sendPushCommentNotification(
        receiverId: String,
        senderName: String,
        commentContent: String,
        postId: String
    ) {
        try {
            val dataSource = FirebaseMessageDataSource()
            val bodyText = "Đã bình luận: ${commentContent.take(100)}"
            dataSource.sendCommentPushNotification(receiverId, senderName, bodyText, postId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNewData(newData: List<Post>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = list.size
            override fun getNewListSize() = newData.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                list[oldPos].postId == newData[newPos].postId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                list[oldPos] == newData[newPos]
        })
        list.clear()
        list.addAll(newData)
        diffResult.dispatchUpdatesTo(this)
    }

    fun clearData() {
        list.clear()
        notifyDataSetChanged()
    }
}