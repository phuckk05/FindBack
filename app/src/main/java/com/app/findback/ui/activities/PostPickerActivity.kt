package com.app.findback.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import com.app.findback.BaseActivity
import com.app.findback.databinding.ActivityPostPickerBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.adapters.PostPickerAdapter
import com.app.findback.ui.viewmodel.PostViewModel
import androidx.recyclerview.widget.LinearLayoutManager

class PostPickerActivity : BaseActivity() {

    private lateinit var binding: ActivityPostPickerBinding
    private val postViewModel: PostViewModel by viewModels()
    private val otherUserId by lazy { intent.getStringExtra("other_user_id") ?: "" }
    private val currentUserId by lazy { intent.getStringExtra("current_user_id") ?: "" }
    private val adapter by lazy {
        PostPickerAdapter { post -> returnPost(post) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(this@PostPickerActivity)
            adapter = this@PostPickerActivity.adapter
        }

        postViewModel.posts.observe(this) { posts ->
            val filtered = if (otherUserId.isEmpty() && currentUserId.isEmpty()) {
                posts
            } else {
                posts.filter { post ->
                    post.userId == otherUserId || post.userId == currentUserId
                }
            }
            adapter.submitList(filtered)
        }


        postViewModel.refreshPosts()
    }

    private fun returnPost(post: Post) {
        val result = Intent().apply {
            putExtra("post_id", post.postId)
            putExtra("post_title", post.title)

            putExtra("post_image", post.imageUrl.ifEmpty {
                post.imageUrls.firstOrNull() ?: ""
            })
            putExtra("post_desc", post.description)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}