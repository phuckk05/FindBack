package com.app.findback.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import com.app.findback.BaseActivity
import com.app.findback.R
import com.app.findback.databinding.ActivityPostDetailBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.adapters.PostImageAdapter
import com.app.findback.utils.extentions.ConvertTime
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class PostDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private var postId: String = ""
    private var currentPost: Post? = null
    private var isSaved = false

    private val db = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getStringExtra("postId") ?: ""

        setupToolbarCus(
            binding.toolbarLayout.toolbar,
            "Chi tiết bài đăng",
            isBack = true,
            isShowSearch = false
        )

        handlePostData()
        setEvents()
    }

    private fun handlePostData() {
        val postFromIntent: Post? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("post", Post::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("post")
        }

        if (postFromIntent != null) {
            currentPost = postFromIntent
            bind(postFromIntent)
            if (postFromIntent.imageUrl.isEmpty() && postFromIntent.imageUrls.isEmpty() ||
                postFromIntent.userAvatar.isEmpty()) {
                loadFullPostFromFirebase(postFromIntent.postId)
            }
        } else {
            loadPostFromFirebase()
        }
    }

    private fun loadFullPostFromFirebase(postId: String) {
        db.getReference("posts").child(postId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val fullPost = Post.fromMap(snap.value as? Map<String, Any?> ?: return@addOnSuccessListener)
                    currentPost = fullPost
                    bind(fullPost)
                }
            }
    }

    private fun loadPostFromFirebase() {
        if (postId.isEmpty()) { finish(); return }
        db.getReference("posts").child(postId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val post = Post.fromMap(snap.value as Map<String, Any?>)
                    currentPost = post
                    bind(post)
                }
            }
    }

    private fun bind(post: Post) {
        binding.tvTitle.text = post.title.ifEmpty { "Không có tiêu đề" }
        binding.tvDescription.text = post.description.ifEmpty { "Không có mô tả" }
        binding.tvName.text = post.userName.ifEmpty { "Người dùng" }
        binding.tvTime.text = if (post.createdAt == 0L) "Vừa xong" else ConvertTime.formatTime(post.createdAt)
        binding.tvStatus.text = if (post.postType == "lost") "Thất lạc" else "Tìm thấy"
        binding.tvStatus.setTextColor(
            getColor(if (post.postType == "lost") R.color.primary_red else R.color.primary_green)
        )

        val images = when {
            post.imageUrls.isNotEmpty() -> post.imageUrls
            post.imageUrl.isNotEmpty() -> listOf(post.imageUrl)
            else -> emptyList()
        }
        setupImagePager(images)
        loadUserAvatar(post.userId)


        val isOwner = post.userId == FirebaseAuth.getInstance().currentUser?.uid
        binding.btnChat.isVisible = !isOwner
        binding.btnSave.isVisible = !isOwner


        if (!isOwner) {
            checkSavedState(post.postId)
        }

        setupMap(post)
    }


    private fun checkSavedState(postId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.getReference("savedPosts").child(uid).child(postId).get()
            .addOnSuccessListener { snap ->
                isSaved = snap.exists()
                updateSaveButton()
            }
    }


    private fun updateSaveButton() {
        if (isSaved) {
            binding.btnSave.text = "Bỏ lưu"
        } else {
            binding.btnSave.text = "Lưu"
        }
    }

    private fun setupImagePager(images: List<String>) {
        if (images.isEmpty()) { binding.viewPagerImages.isVisible = false; return }
        binding.viewPagerImages.adapter = PostImageAdapter(images)
        TabLayoutMediator(binding.tabIndicator, binding.viewPagerImages) { _, _ -> }.attach()
    }

    private fun loadUserAvatar(userId: String) {
        db.getReference("users").child(userId).get()
            .addOnSuccessListener { snap ->
                val avatarUrl = snap.child("avatar").getValue(String::class.java)
                    ?: snap.child("photoUrl").getValue(String::class.java) ?: ""
                Glide.with(this).load(avatarUrl.ifEmpty { R.drawable.logo_tran })
                    .circleCrop().placeholder(R.drawable.logo_tran).into(binding.imgAvatar)
            }
            .addOnFailureListener {
                Glide.with(this).load(R.drawable.logo_tran).circleCrop().into(binding.imgAvatar)
            }
    }

    private fun setupMap(post: Post) {
        if (post.latitude == 0.0 && post.longitude == 0.0) { binding.mapCard.isVisible = false; return }
        val geo = GeoPoint(post.latitude, post.longitude)
        binding.map.post {
            binding.map.controller.setZoom(15.0)
            binding.map.controller.setCenter(geo)
            val marker = Marker(binding.map).apply {
                position = geo
                icon = getDrawable(if (post.postType == "found") R.drawable.ic_location_green else R.drawable.ic_location_red)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            binding.map.overlays.clear()
            binding.map.overlays.add(marker)
            binding.map.invalidate()
        }
    }

    private fun setEvents() {
        binding.btnShare.setOnClickListener {
            currentPost?.let {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://metalk-a52fb.web.app/post/${it.postId}")
                }
                startActivity(Intent.createChooser(intent, "Chia sẻ"))
            }
        }

        binding.btnChat.setOnClickListener { currentPost?.let { openChat(it) } }


        binding.btnSave.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val pid = currentPost?.postId ?: return@setOnClickListener
            val ref = db.getReference("savedPosts").child(uid).child(pid)

            if (isSaved) {
                ref.removeValue().addOnSuccessListener {
                    isSaved = false
                    updateSaveButton()
                    Toast.makeText(this, "Đã bỏ lưu bài viết", Toast.LENGTH_SHORT).show()
                }
            } else {
                ref.setValue(mapOf("postId" to pid, "savedAt" to System.currentTimeMillis()))
                    .addOnSuccessListener {
                        isSaved = true
                        updateSaveButton()
                        Toast.makeText(this, "Đã lưu bài viết", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun openChat(post: Post) {
        val image = when {
            post.imageUrls.isNotEmpty() -> post.imageUrls.first()
            post.imageUrl.isNotEmpty() -> post.imageUrl
            else -> ""
        }
        Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, post.userId)
            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, post.userName)
            putExtra(ChatActivity.EXTRA_OTHER_USER_AVATAR, post.userAvatar)
            putExtra(ChatActivity.EXTRA_SEND_POST_ID, post.postId)
            putExtra(ChatActivity.EXTRA_SEND_POST_TITLE, post.title)
            putExtra(ChatActivity.EXTRA_SEND_POST_IMAGE, image)
            putExtra(ChatActivity.EXTRA_SEND_POST_DESC, post.description)
            putExtra("from_post_detail", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }.also { startActivity(it) }
    }

    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { super.onPause(); binding.map.onPause() }
}