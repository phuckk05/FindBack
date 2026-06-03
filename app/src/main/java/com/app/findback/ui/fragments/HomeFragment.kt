package com.app.findback.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.R
import com.app.findback.databinding.FragmentHomeBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.app.findback.ui.activities.ChatActivity
import com.app.findback.ui.activities.PostDetailActivity
import com.app.findback.ui.activities.SearchPostActivity
import com.app.findback.ui.adapters.HomeAdapter
import com.app.findback.ui.components.toolbar.ToolbarConfig
import com.app.findback.ui.components.toolbar.ToolbarConfigProvider
import com.app.findback.ui.viewmodel.PostViewModel
import com.app.findback.ui.viewmodels.NotificationViewModel
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), ToolbarConfigProvider {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeAdapter: HomeAdapter
    private lateinit var postViewModel: PostViewModel
    private val notificationViewModel: NotificationViewModel by viewModels()

    private var allPosts: List<Post> = emptyList()
    private var currentUnreadCount = 0

    private var pendingTargetPostId: String? = null
    private var pendingScrollToComment: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setControl()
        setEvent()
        observeNotificationBadge()
    }

    fun setPendingNotificationTarget(postId: String, scrollToComment: Boolean) {
        pendingTargetPostId = postId
        pendingScrollToComment = scrollToComment

        if (_binding == null) return

        binding.rvPosts.post {
            scrollAndExpandComment()
        }
    }

    fun handleNotificationNavigation(postId: String, scrollToComment: Boolean) {
        setPendingNotificationTarget(postId, scrollToComment)
    }

    private fun setControl() {
        postViewModel = ViewModelProvider(requireActivity())[PostViewModel::class.java]
        homeAdapter = HomeAdapter(requireContext(), mutableListOf())
    }

    private fun setEvent() {
        setupRecyclerView()
        observePosts()
        setupChipFilter()
    }

    private fun setupRecyclerView() {
        homeAdapter.setHasStableIds(true)

        binding.rvPosts.layoutManager = GridLayoutManager(requireContext(), 1).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
        binding.rvPosts.adapter = homeAdapter

        homeAdapter.setOnItemClickListener(object : HomeAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val post = homeAdapter.getCurrentList()[position]
                val intent = Intent(requireContext(), PostDetailActivity::class.java).apply {
                    putExtra("postId", post.postId)
                    putExtra("post", post)
                }
                startActivity(intent)
            }

            override fun onItemClickShare(position: Int) {
                val postId = homeAdapter.getCurrentList()[position].postId
                val link = "https://metalk-a52fb.web.app/post/$postId"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                startActivity(Intent.createChooser(intent, "Chia sẻ"))
            }

            override fun onItemClickChat(position: Int) {
                openChatWithPostOwner(homeAdapter.getCurrentList()[position])
            }
        })
    }

    private fun observePosts() {
        postViewModel.postsShared.observe(viewLifecycleOwner) { posts ->
            allPosts = posts
            applyCurrentFilter()

            if (pendingTargetPostId != null && pendingScrollToComment) {
                binding.rvPosts.post {
                    scrollAndExpandComment()
                }
            }
        }
    }

    private fun scrollAndExpandComment() {
        val postId = pendingTargetPostId ?: return
        Log.d("NAV_DEBUG", "scrollAndExpandComment: postId=$postId")

        val adapterList = homeAdapter.getCurrentList()
        val position = adapterList.indexOfFirst { it.postId == postId }
        if (position == -1) {
            pendingTargetPostId = null
            pendingScrollToComment = false
            return
        }

        pendingTargetPostId = null
        pendingScrollToComment = false

        val rv = binding.rvPosts
        rv.smoothScrollToPosition(position)

        rv.postDelayed({
            Log.d("NAV_DEBUG", "Calling expandCommentForPost after scroll")
            homeAdapter.expandCommentForPost(postId)

            rv.postDelayed({
                rv.smoothScrollToPosition(position)
            }, 400)
        }, 600)
    }

    private fun observeNotificationBadge() {
        lifecycleScope.launch {
            notificationViewModel.unreadCount.collect { count ->
                currentUnreadCount = count
                refreshToolbar()
            }
        }
    }

    private fun setupChipFilter() {
        binding.cgChip.check(binding.cgChip.getChildAt(0).id)
        binding.cgChip.setOnCheckedStateChangeListener { _, _ ->
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        val checkedId = binding.cgChip.checkedChipId
        val chipText = binding.cgChip.findViewById<Chip>(checkedId)?.text?.toString() ?: ""

        val filteredPosts = when {
            chipText.contains("Thất lạc", ignoreCase = true) -> allPosts.filter { it.postType == "lost" }
            chipText.contains("Tìm thấy", ignoreCase = true) -> allPosts.filter { it.postType == "found" }
            else -> allPosts
        }

        homeAdapter.addNewData(filteredPosts)
    }

    private fun openChatWithPostOwner(post: Post) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (post.userId == currentUserId) {
            Toast.makeText(requireContext(), "Đây là bài viết của bạn", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("from_post_detail", true)
            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, post.userId)
            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, post.userName ?: "Người dùng")
            putExtra(ChatActivity.EXTRA_OTHER_USER_AVATAR, post.userAvatar ?: "")
            putExtra(ChatActivity.EXTRA_SEND_POST_ID, post.postId)
            putExtra(ChatActivity.EXTRA_SEND_POST_TITLE, post.title)
            putExtra(ChatActivity.EXTRA_SEND_POST_IMAGE, post.imageUrl)
            putExtra(ChatActivity.EXTRA_SEND_POST_DESC, post.description)
        }
        startActivity(intent)
    }

    private fun refreshToolbar() {
        (requireActivity() as? BaseBottomNavActivity)?.refreshToolbarForActiveFragment()
    }

    override fun toolbarConfig() = ToolbarConfig(
        titleResId = R.string.app_name,
        isBack = false,
        isShowSearch = false,
        imageLogoRes = R.drawable.logo_tran,
        ib1Res = R.drawable.ic_notification,
        ib2Res = R.drawable.ic_search,
        ib1Badge = currentUnreadCount,
        onIB1 = { openNotificationsScreen() },
        onIB2 = { setEventClickSearch() }
    )

    private fun setEventClickSearch() {
        startActivity(Intent(requireContext(), SearchPostActivity::class.java))
    }

    private fun openNotificationsScreen() {
        val activity = requireActivity()
        if (activity is BaseBottomNavActivity) {
            activity.openNotificationsFragment()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        homeAdapter.removeAllListeners()
        postViewModel.removeListener()
        _binding = null
    }
}