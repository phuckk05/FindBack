package com.app.findback.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.app.findback.BaseActivity
import com.app.findback.R
import com.app.findback.databinding.ActivityBaseBottomNavBinding
import com.app.findback.ui.components.toolbar.ToolbarConfig
import com.app.findback.ui.components.toolbar.ToolbarConfigProvider
import com.app.findback.ui.fragments.AddStatusFragment
import com.app.findback.ui.fragments.HomeFragment
import com.app.findback.ui.fragments.MapFragment
import com.app.findback.ui.fragments.MessageFragment
import com.app.findback.ui.fragments.NotificationsFragment
import com.app.findback.ui.fragments.ProfileFragment
import com.app.findback.ui.viewmodel.CircleZoneViewModel
import com.app.findback.ui.viewmodel.PostViewModel
import kotlin.math.abs

class BaseBottomNavActivity : BaseActivity() {

    private lateinit var binding: ActivityBaseBottomNavBinding

    // Fragments
    private val homeFragment = HomeFragment()
    private val mapFragment = MapFragment()
    private val addFragment = AddStatusFragment()
    private val messageFragment = MessageFragment()
    private val profileFragment = ProfileFragment()

    private lateinit var activeFragment: Fragment

    private val fragmentByItemId: Map<Int, Fragment> by lazy {
        mapOf(
            R.id.nav_home to homeFragment,
            R.id.nav_map to mapFragment,
            R.id.nav_add to addFragment,
            R.id.nav_message to messageFragment,
            R.id.nav_profile to profileFragment
        )
    }

    private lateinit var postViewModel: PostViewModel
    private lateinit var circleZoneViewModel: CircleZoneViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setControl()
        setContentView(binding.root)
        setupDraggableAiChat()
        setBottomNav()

        if (intent.getBooleanExtra("open_message_tab", false)) {
            openMessageFragment()
        }

        setBottomNavInsert()
        handleIntent(intent)
        getPosts()

        // Nếu có extra open_chat từ notification click → mở ChatActivity luôn
        handleOpenChatFromNotification(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    val isNotificationFragment =
                        supportFragmentManager.findFragmentByTag("notifications") != null
                    supportFragmentManager.popBackStack()
                    if (isNotificationFragment) {
                        binding.bottomNav.visibility = View.VISIBLE
                        refreshToolbarForActiveFragment()
                    }
                    return
                }
                finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_message_tab", false)) {
            openMessageFragment()
        }
        handleIntent(intent)

        // Xử lý notification click khi app đang foreground và BaseBottomNavActivity đã tồn tại
        handleOpenChatFromNotification(intent)
    }

    /**
     * Xử lý mở ChatActivity khi user ấn notification từ ngoài app.
     * MainActivity gửi extra "open_chat" = true kèm conversation info.
     */
    private fun handleOpenChatFromNotification(intent: Intent) {
        if (!intent.getBooleanExtra("open_chat", false)) return

        val conversationId = intent.getStringExtra(ChatActivity.EXTRA_CONVERSATION_ID) ?: return
        val otherUserId = intent.getStringExtra(ChatActivity.EXTRA_OTHER_USER_ID) ?: return
        val otherUserName = intent.getStringExtra(ChatActivity.EXTRA_OTHER_USER_NAME) ?: ""

        // Xóa flag để tránh mở lại khi onNewIntent được gọi nhiều lần
        intent.removeExtra("open_chat")

        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, otherUserId)
            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, otherUserName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    val getToolbar get() = binding.toolbarLayout.toolbar

    fun setControl() {
        binding = ActivityBaseBottomNavBinding.inflate(layoutInflater)
        postViewModel = PostViewModel()
        circleZoneViewModel = CircleZoneViewModel()
    }

    var getLat: Double? = null
    var getLng: Double? = null

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.pathSegments.isEmpty()) return

        val tag = data.pathSegments[0]

        when (tag) {
            "map" -> {
                val lat = data.pathSegments[2]
                val lng = data.pathSegments[1]
                getLat = lat.toDouble()
                getLng = lng.toDouble()
                binding.bottomNav.selectedItemId = R.id.nav_map
                binding.bottomNav.post {
                    val mapFragment = supportFragmentManager
                        .fragments.filterIsInstance<MapFragment>().firstOrNull()
                    mapFragment?.zoomToPost(lat.toDouble(), lng.toDouble())
                }
            }
            "post" -> {
                val postId = data.pathSegments[1]
                Log.d("BaseBottomNavActivity", "Received postId from intent: $postId")
                startActivity(Intent(this, PostDetailActivity::class.java).apply {
                    putExtra("postId", postId)
                })
            }
        }
    }

    private fun setBottomNav() {
        binding.bottomNav.itemIconTintList = null
        binding.bottomNav.itemTextColor = createBottomNavTextColors()

        val openMessage = intent.getBooleanExtra("open_message_tab", false)
        val defaultFragment = if (openMessage) messageFragment else homeFragment
        val defaultTab = if (openMessage) R.id.nav_message else R.id.nav_home

        activeFragment = defaultFragment

        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, homeFragment, "homeFragment")
            .add(R.id.fragmentContainer, mapFragment, "mapFragment").hide(mapFragment)
            .add(R.id.fragmentContainer, addFragment, "addFragment").hide(addFragment)
            .add(R.id.fragmentContainer, messageFragment, "messageFragment")
            .add(R.id.fragmentContainer, profileFragment, "profileFragment").hide(profileFragment)
            .apply {
                when (defaultFragment) {
                    homeFragment -> hide(messageFragment)
                    messageFragment -> hide(homeFragment)
                }
            }
            .commit()

        setBottomNavIcons(defaultTab)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetFragment = fragmentByItemId[item.itemId]
                ?: return@setOnItemSelectedListener false
            if (targetFragment === activeFragment) return@setOnItemSelectedListener true
            setBottomNavIcons(item.itemId)
            switchFragment(targetFragment)
            applyToolbarForFragment(targetFragment)
            updateToolbarScrollBehavior(targetFragment)
            true
        }

        applyToolbarForFragment(defaultFragment)
        updateToolbarScrollBehavior(defaultFragment)
        binding.bottomNav.selectedItemId = defaultTab
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment).show(fragment).commit()
        activeFragment = fragment
    }

    private fun applyToolbarForFragment(fragment: Fragment) {
        val config = (fragment as? ToolbarConfigProvider)?.toolbarConfig()
            ?: ToolbarConfig(titleResId = R.string.app_name)
        setupToolbarCus(
            toolbar = getToolbar,
            title = getString(config.titleResId),
            isShowSearch = config.isShowSearch,
            backgroudResId = config.backgroudResId,
            isBack = config.isBack,
            imageLogo = config.imageLogoRes,
            ib1 = config.ib1Res,
            ib2 = config.ib2Res,
            ib1Badge = config.ib1Badge,
            ib2Badge = config.ib2Badge,
            onIB1 = config.onIB1,
            onIB2 = config.onIB2
        )
    }

    fun refreshToolbarForActiveFragment() {
        applyToolbarForFragment(activeFragment)
    }

    fun getToolbarSearchInput(): EditText = binding.toolbarLayout.etSearch

    private fun updateToolbarScrollBehavior(fragment: Fragment) {
        val toolbarLayoutParams = getToolbar.layoutParams as? AppBarLayout.LayoutParams ?: return
        val appBarLayout = binding.toolbarLayout.root as? AppBarLayout ?: return
        val isHome = fragment === homeFragment
        val targetFlags = if (isHome) {
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        } else 0
        if (toolbarLayoutParams.scrollFlags != targetFlags) {
            toolbarLayoutParams.scrollFlags = targetFlags
            getToolbar.layoutParams = toolbarLayoutParams
        }
        if (!isHome) appBarLayout.setExpanded(true, true)
    }

    private fun createBottomNavTextColors(): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.primary_blue),
            ContextCompat.getColor(this, R.color.bottom_nav_unselected)
        )
        return ColorStateList(states, colors)
    }

    private fun setBottomNavIcons(selectedItemId: Int) {
        binding.bottomNav.menu.findItem(R.id.nav_home).setIcon(
            if (selectedItemId == R.id.nav_home) R.drawable.ic_home else R.drawable.ic_home_grey
        )
        binding.bottomNav.menu.findItem(R.id.nav_map).setIcon(
            if (selectedItemId == R.id.nav_map) R.drawable.ic_map else R.drawable.ic_map_grey
        )
        binding.bottomNav.menu.findItem(R.id.nav_add).setIcon(
            if (selectedItemId == R.id.nav_add) R.drawable.ic_add else R.drawable.ic_add_grey
        )
        binding.bottomNav.menu.findItem(R.id.nav_message).setIcon(
            if (selectedItemId == R.id.nav_message) R.drawable.ic_message else R.drawable.ic_message_grey
        )
        binding.bottomNav.menu.findItem(R.id.nav_profile).setIcon(
            if (selectedItemId == R.id.nav_profile) R.drawable.ic_profile else R.drawable.ic_profile_grey
        )
    }

    private fun setBottomNavInsert() {
        setupBottomNavInsertCus(binding.bottomNav)
    }

    private fun getPosts() {
        postViewModel.getPosts()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableAiChat() {
        val parentView = binding.main
        val chatView = binding.floatingChat.chatBoxAi
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var downRawX = 0f
        var downRawY = 0f
        var dX = 0f
        var dY = 0f
        var isDragging = false

        chatView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dX = v.x - downRawX
                    dY = v.y - downRawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveX = event.rawX - downRawX
                    val moveY = event.rawY - downRawY
                    if (!isDragging && (abs(moveX) > touchSlop || abs(moveY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val insets = ViewCompat.getRootWindowInsets(parentView)
                            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        val leftBound = (insets?.left ?: 0).toFloat()
                        val topBound = (insets?.top ?: 0).toFloat()
                        val rightBound = (parentView.width - v.width - (insets?.right ?: 0)).toFloat()
                        val navHeight = binding.bottomNav.height
                        val gap = (12 * resources.displayMetrics.density).toInt()
                        val bottomBound = (parentView.height - v.height - (insets?.bottom ?: 0) - navHeight - gap).toFloat()
                        v.x = (event.rawX + dX).coerceIn(leftBound, rightBound)
                        v.y = (event.rawY + dY).coerceIn(topBound, bottomBound)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) v.performClick()
                    true
                }
                else -> false
            }
        }

        chatView.setOnClickListener {
            startActivity(Intent(this, ChatAIActivity::class.java))
        }
    }

    private fun openMessageFragment() {
        if (activeFragment === messageFragment) return
        setBottomNavIcons(R.id.nav_message)
        supportFragmentManager.beginTransaction()
            .hide(activeFragment).show(messageFragment).commit()
        activeFragment = messageFragment
        applyToolbarForFragment(messageFragment)
        updateToolbarScrollBehavior(messageFragment)
        binding.bottomNav.menu.findItem(R.id.nav_message).isChecked = true
    }

    fun openNotificationsFragment() {
        val notificationsFragment = NotificationsFragment()
        binding.bottomNav.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .replace(R.id.fragmentContainer, notificationsFragment, "notifications")
            .addToBackStack("notifications")
            .commitAllowingStateLoss()
        binding.bottomNav.postDelayed({
            applyToolbarForFragment(notificationsFragment)
        }, 150)
    }
}