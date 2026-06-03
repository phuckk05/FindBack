package com.app.findback.ui.adapters

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.databinding.ItemMessagePostAiBinding
import com.app.findback.databinding.ItemMessagePostBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.activities.PostDetailActivity

class PostAiAdapter(
    private var items: List<Post> = emptyList(),
    private val onClick: ((Post) -> Unit)? = null
) : RecyclerView.Adapter<PostAiAdapter.PostViewHolder>() {

    fun submitList(list: List<Post>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemMessagePostAiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(items[position])


    }

    override fun getItemCount(): Int = items.size

    inner class PostViewHolder(private val binding: ItemMessagePostAiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            // Hiển thị title hoặc fallback
            binding.textUsername.text = post.userName.ifBlank { "Chưa có tên" }
            binding.textContent.text = post.title.ifBlank { post.locationText.ifBlank { post.postId } }
            itemView.setOnClickListener {
                //chuyển qua detail post
                val postId = post.postId
                val intent = Intent(itemView.context, PostDetailActivity::class.java)
                intent.putExtra("postId", postId)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                itemView.context.startActivity(intent)
            }
        }
    }
}