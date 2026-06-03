package com.app.findback.data.source.gemini

import android.util.Log
import com.app.findback.data.source.remote.FirebaseChatAiDataSource
import com.app.findback.domain.models.AiResponse
import com.app.findback.domain.models.ChatMessage
import com.app.findback.domain.models.ChatSession
import com.app.findback.domain.models.Post
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiChatService {
    private val firebase = FirebaseChatAiDataSource()
    private val apiKey = ""
    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun sendMessage(
        session: ChatSession,
        userMessage: String,
        posts: List<Post>
    ){

        // chạy toàn bộ logic nặng trong IO dispatcher
        withContext(Dispatchers.IO) {
            try {
                val time = System.currentTimeMillis()

                // user message
                val userMsg = ChatMessage(
                    id = time.toString(),
                    content = userMessage,
                    isUser = true,
                    timestamp = time,
                    postId = List(posts.size) { posts[it].postId ?: "" }
                )

                // lưu Firebase (non-blocking callback)
                firebase.sendMessage(session.userId, userMsg)

                // giữ local
                session.messages.add(userMsg)

                // BUILD PROMPT
                val prompt = buildChatPrompt(session, posts)

                var (replyText, postIds) = try {
                    val response = model.generateContent(prompt)

                    val raw = response.text ?: ""
                    Log.d("AI_RAW", raw)

                    val clean = raw
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    val aiResponse = try {
                        Gson().fromJson(clean, AiResponse::class.java)
                    } catch (e: Exception) {
                        Log.e("AI_PARSE", "Parse error", e)
                        null
                    }

                    val reply = aiResponse?.reply ?: clean
                    val ids = aiResponse?.postIds ?: emptyList()

                    Log.d("AI_PARSED", "reply=$reply | ids=$ids")

                    Pair(reply, ids)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(
                        "Xin lỗi, hiện tại không thể kết nối tới dịch vụ AI.",
                        emptyList()
                    )
                }

                // AI MESSAGE
                val aiTime = System.currentTimeMillis()
                if (postIds.isEmpty()){
                    postIds = listOf("-1")
                }
                val aiMsg = ChatMessage(
                    id = aiTime.toString(),
                    content = replyText,
                    isUser = false,
                    timestamp = aiTime,
                    postId = postIds
                )

                // lưu Firebase cho AI message (non-blocking)
                firebase.sendMessage(session.userId, aiMsg)

            } catch (e: Exception) {
                // bắt mọi lỗi bất ngờ để tránh crash
                e.printStackTrace()
                // (tuỳ) bạn có thể post lỗi lên ViewModel để UI thông báo
            }
        }
    }
    //xây dựng prompt chuẩn
    private fun buildChatPrompt(
        session: ChatSession,
        posts: List<Post>
    ): String {

        val history = session.messages.joinToString("\n") {
            if (it.isUser) {
                "User: ${it.content}"
            } else {
                "AI: ${it.content}"
            }
        }

        val postsJson = Gson().toJson(posts)

        return """
Bạn là AI của app FindBack (tìm đồ thất lạc).

DỮ LIỆU các bài đăng:
$postsJson

LỊCH SỬ CHAT:
$history

NHIỆM VỤ:
- Trả lời thân thiện bằng tiếng Việt
- Tìm các bài đăng phù hợp với yêu cầu người dùng

QUAN TRỌNG:
LUÔN trả về JSON đúng format sau:

{
  "reply": "nội dung trả lời cho user",
  "postIds": ["id1", "id2", "id3"]
}

QUY TẮC:
- reply: luôn là câu trả lời tự nhiên, không chứa id của bài post
- postIds: danh sách id bài đăng phù hợp (có thể rỗng [])
- KHÔNG trả thêm text ngoài JSON
""".trimIndent()
    }
}