package com.app.findback.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.findback.databinding.FragmentSavedPostsBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.activities.ChatActivity
import com.app.findback.ui.activities.PostDetailActivity
import com.app.findback.ui.adapters.HomeAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SavedPostsFragment : Fragment() {

    private var _binding: FragmentSavedPostsBinding? = null
    private val binding get() = _binding!!

    private val savedList = mutableListOf<Post>()
    private lateinit var adapter: HomeAdapter

    var onSavedCountLoaded: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadSavedPosts()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            savedList.clear()
            adapter.notifyDataSetChanged()
            loadSavedPosts()
        }
    }

    private fun setupRecyclerView() {
        adapter = HomeAdapter(requireContext(), savedList)

        binding.rvSavedPosts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SavedPostsFragment.adapter
            setHasFixedSize(false)
        }

        adapter.setOnItemClickListener(object : HomeAdapter.OnItemClickListener {

            // Ấn vào item → mở PostDetailActivity
            override fun onItemClick(position: Int) {
                val post = savedList[position]
                val intent = Intent(requireContext(), PostDetailActivity::class.java).apply {
                    putExtra("postId", post.postId)
                    putExtra("post", post)
                }
                startActivity(intent)
            }

            // Ấn Share → share link
            override fun onItemClickShare(position: Int) {
                val post = savedList[position]
                val link = "https://metalk-a52fb.web.app/post/${post.postId}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                startActivity(Intent.createChooser(intent, "Chia sẻ"))
            }

            // Ấn Chat → mở ChatActivity
            override fun onItemClickChat(position: Int) {
                val post = savedList[position]
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

                // Không chat với chính mình
                if (post.userId == currentUid) return

                val image = when {
                    post.imageUrls.isNotEmpty() -> post.imageUrls.first()
                    post.imageUrl.isNotEmpty() -> post.imageUrl
                    else -> ""
                }

                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("from_post_detail", true)
                    putExtra(ChatActivity.EXTRA_OTHER_USER_ID, post.userId)
                    putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, post.userName)
                    putExtra(ChatActivity.EXTRA_OTHER_USER_AVATAR, post.userAvatar)
                    putExtra(ChatActivity.EXTRA_SEND_POST_ID, post.postId)
                    putExtra(ChatActivity.EXTRA_SEND_POST_TITLE, post.title)
                    putExtra(ChatActivity.EXTRA_SEND_POST_IMAGE, image)
                    putExtra(ChatActivity.EXTRA_SEND_POST_DESC, post.description)
                }
                startActivity(intent)
            }
        })
    }

    private fun loadSavedPosts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()

        binding.progressBar.isVisible = true
        binding.layoutEmpty.isVisible = false

        db.getReference("savedPosts").child(uid).get()
            .addOnSuccessListener { savedSnap ->
                if (_binding == null) return@addOnSuccessListener

                val postIds = savedSnap.children
                    .mapNotNull { it.child("postId").getValue(String::class.java) }
                    .filter { it.isNotEmpty() }

                if (postIds.isEmpty()) {
                    binding.progressBar.isVisible = false
                    binding.layoutEmpty.isVisible = true
                    onSavedCountLoaded?.invoke(0)
                    return@addOnSuccessListener
                }

                val posts = mutableListOf<Post>()
                var loadedCount = 0

                for (pid in postIds) {
                    db.getReference("posts").child(pid).get()
                        .addOnSuccessListener { postSnap ->
                            if (_binding == null) return@addOnSuccessListener

                            if (postSnap.exists()) {
                                runCatching {
                                    Post.fromMap(postSnap.value as Map<String, Any?>)
                                }.onSuccess { post -> posts.add(post) }
                            }

                            loadedCount++

                            if (loadedCount == postIds.size) {
                                val sorted = posts.sortedByDescending { it.createdAt }
                                savedList.clear()
                                savedList.addAll(sorted)
                                adapter.notifyDataSetChanged()
                                binding.progressBar.isVisible = false
                                binding.layoutEmpty.isVisible = sorted.isEmpty()
                                onSavedCountLoaded?.invoke(sorted.size)
                            }
                        }
                        .addOnFailureListener {
                            if (_binding == null) return@addOnFailureListener
                            loadedCount++
                            if (loadedCount == postIds.size) {
                                binding.progressBar.isVisible = false
                                binding.layoutEmpty.isVisible = savedList.isEmpty()
                                onSavedCountLoaded?.invoke(savedList.size)
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.isVisible = false
                binding.layoutEmpty.isVisible = true
                onSavedCountLoaded?.invoke(0)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}