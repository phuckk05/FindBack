package com.app.findback.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.findback.BaseActivity
import com.app.findback.R
import com.app.findback.data.repositories.MessageRepositoryImpl
import com.app.findback.data.source.remote.FirebaseMessageDataSource
import com.app.findback.databinding.ActivityChatBinding
import com.app.findback.domain.models.MessageLocation
import com.app.findback.domain.models.MessagePost
import com.app.findback.ui.adapters.ChatAdapter
import com.app.findback.ui.viewmodel.MessageViewModel
import com.app.findback.ui.viewmodel.MessageViewModelFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.location.LocationServices
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conv_id"
        const val EXTRA_OTHER_USER_ID = "other_user_id"
        const val EXTRA_OTHER_USER_NAME = "other_user_name"
        const val EXTRA_OTHER_USER_AVATAR = "other_user_avatar"

        const val EXTRA_SEND_POST_ID = "SEND_POST_ID"
        const val EXTRA_SEND_POST_TITLE = "SEND_POST_TITLE"
        const val EXTRA_SEND_POST_IMAGE = "SEND_POST_IMAGE"
        const val EXTRA_SEND_POST_DESC = "SEND_POST_DESC"
    }

    private lateinit var binding: ActivityChatBinding

    private val viewModel: MessageViewModel by viewModels {
        MessageViewModelFactory(MessageRepositoryImpl(FirebaseMessageDataSource()))
    }

    private val otherUserId by lazy { intent.getStringExtra(EXTRA_OTHER_USER_ID) ?: "" }
    private val otherUserName by lazy { intent.getStringExtra(EXTRA_OTHER_USER_NAME) ?: "Chat" }
    private val otherUserAvatar by lazy { intent.getStringExtra(EXTRA_OTHER_USER_AVATAR) ?: "" }

    private val conversationId by lazy {
        intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: viewModel.getConversationId(otherUserId)
    }

    private var pendingPost: MessagePost? = null

    private val chatAdapter by lazy {
        ChatAdapter(
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            onLocationClick = { lat, lng -> openGoogleMaps(lat, lng) },
            onPostClick = { postId -> openPostDetail(postId) }
        )
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchAndSendLocation() else showToast("Không có quyền vị trí")
    }

    private val postPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val post = MessagePost(
                postId = data?.getStringExtra("post_id") ?: "",
                title = data?.getStringExtra("post_title") ?: "",
                imageUrl = data?.getStringExtra("post_image") ?: "",
                description = data?.getStringExtra("post_desc") ?: ""
            )
            if (post.postId.isNotEmpty()) {
                pendingPost = post
                showPostPreview()
                binding.layoutExtraActions.isVisible = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setKeybroad()

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeMessages()
        observeSendState()

        viewModel.loadMessages(
            conversationId,
            FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )

        handlePostPreviewFromIntent(intent)
    }

    fun getCurrentConversationId(): String = conversationId


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val newConvId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (newConvId.isNotEmpty() && newConvId != conversationId) {
            finish()
            startActivity(intent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }


        handlePostPreviewFromIntent(intent)
    }


    private fun handlePostPreviewFromIntent(intent: Intent) {
        val postId = intent.getStringExtra(EXTRA_SEND_POST_ID)
        if (postId.isNullOrEmpty()) return

        pendingPost = MessagePost(
            postId = postId,
            title = intent.getStringExtra(EXTRA_SEND_POST_TITLE) ?: "",
            imageUrl = intent.getStringExtra(EXTRA_SEND_POST_IMAGE) ?: "",
            description = intent.getStringExtra(EXTRA_SEND_POST_DESC) ?: ""
        )
        showPostPreview()
    }

    private fun showPostPreview() {
        pendingPost?.let { post ->
            binding.layoutPostPreview.isVisible = true

            binding.tvPreviewPostTitle.text = post.title
            binding.tvPreviewPostDesc.text = post.description.take(100) +
                    if (post.description.length > 100) "..." else ""

            Glide.with(this)
                .load(post.imageUrl.ifEmpty { null })
                .apply(RequestOptions().error(R.drawable.ic_post))
                .into(binding.ivPreviewPostImage)

            // BUG FIX: Nút "Chọn bài khác" mở PostPicker, không phải btnSendPost dưới input
            binding.btnSendPost.setOnClickListener {
                postPickerLauncher.launch(Intent(this, PostPickerActivity::class.java).apply {
                    putExtra("other_user_id", otherUserId)
                    putExtra("current_user_id", FirebaseAuth.getInstance().currentUser?.uid ?: "")
                })
            }

            binding.btnCancelPostPreview.setOnClickListener {
                clearPostPreview()
            }

            binding.btnSendPostPreview.setOnClickListener {
                viewModel.sendPostMessage(otherUserId, post)
                clearPostPreview()
            }
        }
    }

    private fun clearPostPreview() {
        binding.layoutPostPreview.isVisible = false
        pendingPost = null
    }

    private fun setupToolbar() {
        binding.tvToolbarName.text = otherUserName
        Glide.with(this)
            .load(otherUserAvatar.ifEmpty { null })
            .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_default_avatar))
            .into(binding.ivToolbarAvatar)

        binding.btnBack.setOnClickListener {
            if (intent.getBooleanExtra("from_post_detail", false)) {
                setResult(
                    RESULT_OK,
                    Intent().apply { putExtra("open_message_tab", true) }
                )
            }
            finish()
        }
    }

    private fun setupRecyclerView() {
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            adapter = chatAdapter
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotBlank()) {
                viewModel.sendTextMessage(otherUserId, text)
                binding.etMessage.setText("")
            }
        }

        binding.btnSendLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fetchAndSendLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        binding.btnSendPost.setOnClickListener {
            postPickerLauncher.launch(Intent(this, PostPickerActivity::class.java).apply {
                putExtra("other_user_id", otherUserId)
                putExtra("current_user_id", FirebaseAuth.getInstance().currentUser?.uid ?: "")
            })
        }

        binding.btnAttach.setOnClickListener {
            binding.layoutExtraActions.isVisible = !binding.layoutExtraActions.isVisible
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                        viewModel.markAsRead(conversationId)
                    }
                }
            }
        }
    }

    private fun observeSendState() {
        lifecycleScope.launch {
            viewModel.sendState.collect { state ->
                if (state is MessageViewModel.SendState.Error) showToast(state.msg)
            }
        }
    }

    private fun fetchAndSendLocation() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        try {
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val msgLocation = MessageLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = "Chia sẻ vị trí hiện tại"
                    )
                    viewModel.sendLocationMessage(otherUserId, msgLocation)
                    binding.layoutExtraActions.isVisible = false
                } else {
                    showToast("Không lấy được vị trí")
                }
            }
        } catch (_: SecurityException) {
            showToast("Lỗi quyền vị trí")
        }
    }

    private fun openGoogleMaps(lat: Double, lng: Double) {
        val geoUri = "geo:$lat,$lng?q=$lat,$lng".toUri()
        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapsIntent.resolveActivity(packageManager) != null) {
            startActivity(mapsIntent)
        } else {
            startActivity(
                Intent(Intent.ACTION_VIEW, "https://maps.google.com/?q=$lat,$lng".toUri())
            )
        }
    }


    private fun openPostDetail(postId: String) {
        Log.d("CHAT_POST", "OPEN POST = $postId")
        if (postId.isBlank()) {
            showToast("Không tìm thấy bài viết")
            return
        }
        startActivity(Intent(this, PostDetailActivity::class.java).apply {
            putExtra("postId", postId)
        })
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}