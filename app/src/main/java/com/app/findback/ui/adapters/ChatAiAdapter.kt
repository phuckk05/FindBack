package com.app.findback.ui.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.databinding.ItemMessageReceivedAiBinding
import com.app.findback.databinding.ItemMessageReceivedBinding
import com.app.findback.databinding.ItemMessageSentBinding
import com.app.findback.domain.models.ChatMessage
import com.app.findback.domain.models.Post

class ChatAiAdapter(
    private val messages: MutableList<ChatMessage>,
    private val posts: MutableList<Post>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_AI = 2
    }

    fun submitList(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }
    fun submitListPosts(list: List<Post>) {
        posts.clear()
        posts.addAll(list)
        notifyDataSetChanged()
    }
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.d("ChatAiAdapter", "onCreateViewHolder: viewType=$viewType")

        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageSentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            UserViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedAiBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            AiViewHolder(binding)
        }
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = messages[position]

        when (holder) {
            is UserViewHolder -> holder.bind(item)
            is AiViewHolder -> holder.bind(item,posts)
        }
    }

    // USER
    class UserViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage) {
            binding.tvMessage.text = item.content
        }
    }

    // AI
    class AiViewHolder(private val binding: ItemMessageReceivedAiBinding) : RecyclerView.ViewHolder(binding.root) {
        private val postsAdapter = PostAiAdapter(emptyList())

        init {
            binding.recyclerViewPosts.layoutManager = LinearLayoutManager(binding.root.context, LinearLayoutManager.VERTICAL, false)
            binding.recyclerViewPosts.isNestedScrollingEnabled = false
            binding.recyclerViewPosts.adapter = postsAdapter
        }


        fun bind(item: ChatMessage, allPosts: List<Post>) {
            binding.textViewMessage.text = item.content

            val matchedPosts = item.postId.mapNotNull { id ->
                allPosts.find { it.postId == id }
            }

            if (matchedPosts.isEmpty()) {
                binding.recyclerViewPosts.visibility = View.GONE
            } else {
                Log.d("abs",matchedPosts.toString())
                binding.recyclerViewPosts.visibility = View.VISIBLE
                postsAdapter.submitList(matchedPosts)
            }
        }
    }
}