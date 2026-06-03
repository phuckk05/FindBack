package com.app.findback.ui.components.chatbox

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.findback.databinding.FragmentChatBinding
import com.app.findback.domain.models.ChatMessage
import com.app.findback.domain.models.ChatSession
import com.app.findback.domain.models.Post
import com.app.findback.ui.adapters.ChatAiAdapter
import com.app.findback.ui.viewmodel.ChatAiViewModel
import com.app.findback.ui.viewmodel.PostViewModel
import com.google.firebase.auth.FirebaseAuth


class ChatFragment : Fragment() {
    private lateinit var adapter: ChatAiAdapter
    private lateinit var chatAiViewModel : ChatAiViewModel
    private lateinit var postViewModel: PostViewModel
    private var allMessages: List<ChatMessage> = emptyList()
    private var allPosts: List<Post> = emptyList()
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "0"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setControl()
        setupRecyclerView()
        observeMessagesFromViewModel()
        setEvent()
    }
    //set control
    private fun setControl(){
        chatAiViewModel = ViewModelProvider(requireActivity())[ChatAiViewModel::class.java]
        postViewModel = ViewModelProvider(requireActivity())[PostViewModel::class.java]
    }

    // khởi tạo recyclerview
    private fun setupRecyclerView() {
        adapter = ChatAiAdapter(mutableListOf(), mutableListOf())

        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@ChatFragment.adapter
        }
    }

    // observe LiveData -> update adapter ngay khi có dữ liệu realtime
    private fun observeMessagesFromViewModel() {
        chatAiViewModel.messages.observe(viewLifecycleOwner) { mes ->
            // mes is List<ChatMessage>
            allMessages = mes
            adapter.submitList(allMessages) // adapter sẽ hiển thị lại toàn bộ list
            // scroll to bottom
            binding.recyclerViewChat.post {
                if (adapter.itemCount > 0) {
                    binding.recyclerViewChat.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
        postViewModel.postsShared.observe(viewLifecycleOwner) { posts ->
            allPosts = posts
            adapter.submitListPosts(allPosts)
        }
    }

    // xử lý gửi tin nhắn
    private fun setEvent() {
        // gọi lấy messages realtime (ViewModel sẽ lắng nghe DB và update LiveData)
        chatAiViewModel.getMessages(currentUserId)

        binding.btnSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isEmpty()) return@setOnClickListener

            // tạo message local để hiển thị ngay (optimistic)
            val now = System.currentTimeMillis()
            val localMsg = ChatMessage(
                id = now.toString(),
                content = messageText,
                isUser = true,
                timestamp = now
            )

            // update UI ngay
//            adapter.addMessage(localMsg)
            binding.recyclerViewChat.post {
                binding.recyclerViewChat.scrollToPosition(adapter.itemCount - 1)
            }
            binding.editTextMessage.text.clear()

            // chuẩn bị session: tạo bản sao mutable của lịch sử (không ép kiểu)
            val session = ChatSession(currentUserId, messages = allMessages.toMutableList())

            // call viewmodel để gửi thật lên Firebase + AI
            // bọc try/catch ở viewmodel/ repository để handle lỗi (xem phần dưới)
            chatAiViewModel.sendMessage(messageText, session, allPosts)
        }
        binding.recyclerViewChat.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    hideKeyboard()
                }
                return false
            }
        })
    }
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // lấy view đang có focus, nếu null thì dùng root
        val current = requireActivity().currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(current.windowToken, 0)
        // clear focus khỏi EditText để keyboard không bật lại
        binding.editTextMessage.clearFocus()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}