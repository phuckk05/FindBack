package com.app.findback.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.findback.databinding.FragmentMyPostsBinding
import com.app.findback.domain.models.Post
import com.app.findback.ui.adapters.MyPostAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MyPostsFragment : Fragment() {

    private var _binding: FragmentMyPostsBinding? = null
    private val binding get() = _binding!!

    private val postList = mutableListOf<Post>()
    private lateinit var adapter: MyPostAdapter

    // Callback để notify ProfileFragment cập nhật số đếm
    var onPostCountLoaded: ((Int) -> Unit)? = null

    private var postsListener: ValueEventListener? = null
    private var postsRef: Query? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadMyPosts()
    }

    private fun setupRecyclerView() {
        adapter = MyPostAdapter(
            list = postList,
            isMyPost = true,

            onEdit = { post ->
                val bottomSheet = EditPostBottomSheet.newInstance(post)
                bottomSheet.show(parentFragmentManager, "EditPost")
            },

            onDelete = { post ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn có chắc muốn xóa bài đăng này không?")
                    .setCancelable(false)
                    .setPositiveButton("Xóa") { _, _ ->
                        FirebaseDatabase.getInstance()
                            .getReference("posts")
                            .child(post.postId)
                            .removeValue()
                            .addOnSuccessListener {
                                if (_binding == null) return@addOnSuccessListener
                                Toast.makeText(requireContext(), "Đã xóa bài đăng", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                if (_binding == null) return@addOnFailureListener
                                Toast.makeText(requireContext(), "Xóa thất bại: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Hủy") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        )

        binding.rvMyPosts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyPostsFragment.adapter
        }
    }

    private fun loadMyPosts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val ref = FirebaseDatabase.getInstance()
            .getReference("posts")
            .orderByChild("userId")
            .equalTo(uid)

        postsRef = ref

        postsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                postList.clear()

                for (data in snapshot.children) {
                    runCatching {
                        Post.fromMap(data.value as Map<String, Any?>)
                    }.onSuccess { postList.add(it) }
                }

                postList.sortByDescending { it.createdAt }
                adapter.notifyDataSetChanged()

                binding.layoutEmpty.visibility =
                    if (postList.isEmpty()) View.VISIBLE else View.GONE

                onPostCountLoaded?.invoke(postList.size)
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
            }
        }

        ref.addValueEventListener(postsListener!!)
    }

    override fun onDestroyView() {
        postsListener?.let { postsRef?.removeEventListener(it) }
        _binding = null
        super.onDestroyView()
    }
}