package com.app.findback.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.app.findback.LoginActivity
import com.app.findback.R
import com.app.findback.databinding.FragmentProfileBinding
import com.app.findback.ui.components.toolbar.ToolbarConfig
import com.app.findback.ui.components.toolbar.ToolbarConfigProvider
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileFragment : Fragment(), ToolbarConfigProvider {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    // Giữ reference để gọi reload khi cần
    private var myPostsFragment: MyPostsFragment? = null
    private var savedPostsFragment: SavedPostsFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()

        loadUserInfo()
        setupViewPager()
        setupActions()

        return binding.root
    }

    // ─── ViewPager2 + TabLayout ───────────────────────────────────────────────

    private fun setupViewPager() {
        val pagerAdapter = ProfilePagerAdapter(requireActivity())
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false // tắt swipe để không conflict NestedScrollView

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Bài đăng" else "Đã lưu"
        }.attach()

        // Lắng nghe page change để lazy-load đúng fragment
        binding.viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Có thể thêm logic reload nếu cần
            }
        })
    }

    /**
     * FragmentStateAdapter tạo 2 fragment:
     *   0 → MyPostsFragment   (Bài đăng của tôi)
     *   1 → SavedPostsFragment (Đã lưu)
     */
    private inner class ProfilePagerAdapter(
        fa: FragmentActivity
    ) : FragmentStateAdapter(fa) {

        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MyPostsFragment().also { f ->
                    myPostsFragment = f
                    f.onPostCountLoaded = { count ->
                        if (_binding != null) {
                            binding.txtPostCount.text = count.toString()
                        }
                    }
                }
                else -> SavedPostsFragment().also { f ->
                    savedPostsFragment = f
                    f.onSavedCountLoaded = { count ->
                        if (_binding != null) {
                            binding.txtSavedCount.text = count.toString()
                        }
                    }
                }
            }
        }
    }

    // ─── Load user info ───────────────────────────────────────────────────────

    private fun loadUserInfo() {
        val user = auth.currentUser ?: return
        binding.txtEmail.text = user.email ?: ""

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                val fullName = snapshot.child("fullName").value?.toString() ?: "Người dùng"
                val avatar = snapshot.child("avatar").value?.toString() ?: ""

                binding.txtName.text = fullName

                if (avatar.isNotEmpty()) {
                    Glide.with(requireContext())
                        .load(avatar)
                        .placeholder(R.drawable.logo_tran)
                        .circleCrop()
                        .into(binding.imgAvatar)
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.txtName.text = user.displayName ?: "Người dùng"
            }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnEditProfile.setOnClickListener {
            val bottomSheet = EditProfileBottomSheet()
            bottomSheet.setOnDismissListener { loadUserInfo() }
            bottomSheet.show(parentFragmentManager, "EditProfileBottomSheet")
        }

        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn chắc chắn muốn đăng xuất?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    auth.signOut()
                    if (_binding == null) return@setPositiveButton

                    Toast.makeText(requireContext(), "Đăng xuất thành công", Toast.LENGTH_SHORT).show()

                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────────

    override fun toolbarConfig() = ToolbarConfig(
        titleResId = R.string.nav_profile,
        isBack = false,
        isShowSearch = false
    )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}