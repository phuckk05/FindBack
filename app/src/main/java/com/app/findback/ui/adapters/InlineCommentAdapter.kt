package com.app.findback.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.R
import com.app.findback.domain.models.Comment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class InlineCommentAdapter(
    private val context: Context,
    private val onDelete: (Comment) -> Unit
) : ListAdapter<Comment, InlineCommentAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgInlineCommentAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvInlineCommentName)
        val tvContent: TextView = itemView.findViewById(R.id.tvInlineCommentContent)
        val tvTime: TextView = itemView.findViewById(R.id.tvInlineCommentTime)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteInlineComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_inline_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)

        holder.tvName.text = comment.userName.ifEmpty { "Người dùng" }
        holder.tvContent.text = comment.content
        holder.tvTime.text = getRelativeTime(comment.createdAt)

        Glide.with(context)
            .load(comment.userAvatar.ifEmpty { R.drawable.logo_tran })
            .circleCrop()
            .placeholder(R.drawable.logo_tran)
            .into(holder.imgAvatar)

        val isOwner = comment.userId == FirebaseAuth.getInstance().currentUser?.uid
        holder.btnDelete.visibility = if (isOwner) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { onDelete(comment) }
    }

    private fun getRelativeTime(createdAt: Long): String {
        if (createdAt <= 0L) return "Vừa xong"
        val diff = System.currentTimeMillis() - createdAt
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "Vừa xong"
            minutes < 60 -> "${minutes} phút"
            hours < 24 -> "${hours} giờ"
            else -> "${days} ngày"
        }
    }

    // Đổi thành object (static) để fix lỗi inner class
    object DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.commentId == newItem.commentId
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}