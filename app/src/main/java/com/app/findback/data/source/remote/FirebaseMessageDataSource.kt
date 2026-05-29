package com.app.findback.data.source.remote

import com.app.findback.domain.models.*
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.onesignal.OneSignal
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseMessageDataSource {
    private val client = OkHttpClient()
    private val db = FirebaseDatabase.getInstance().reference

    //info users

    data class UserInfo(
        val fullName: String,
        val avatar: String
    )

    //lay thong tin tu bang user
    private suspend fun getUserInfo(userId: String): UserInfo {
        return try {
            val snapshot = db.child("users").child(userId).get().await()
            UserInfo(
                fullName = snapshot.child("fullName").getValue(String::class.java)
                    ?: "Người dùng",
                avatar = snapshot.child("avatar").getValue(String::class.java)
                    ?: snapshot.child("photoUrl").getValue(String::class.java)
                    ?: ""
            )
        } catch (e: Exception) {
            UserInfo(fullName = "Người dùng", avatar = "")
        }
    }

    // mess

    fun getMessages(
        conversationId: String,
        currentUserId: String
    ): Flow<List<Message>> = callbackFlow {

        val convRef = db.child("conversations").child(conversationId)
        val msgRef = db.child("messages").child(conversationId)
            .orderByChild("timestamp")

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                convRef.get().addOnSuccessListener { convSnap ->

                    val deletedTime =
                        convSnap.child("deleted_$currentUserId")
                            .getValue(Long::class.java) ?: 0L

                    val list = snapshot.children
                        .mapNotNull { it.toMessage() }
                        .filter { it.timestamp > deletedTime }

                    trySend(list)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        msgRef.addValueEventListener(listener)

        awaitClose {
            msgRef.removeEventListener(listener)
        }
    }

    suspend fun sendMessage(message: Message) {
        val convRef = db.child("messages").child(message.conversationId)
        val msgId = message.messageId.ifEmpty { convRef.push().key ?: return }
        val finalMsg = message.copy(messageId = msgId)

        convRef.child(msgId).setValue(finalMsg.toMap()).await()
        updateConversation(finalMsg)
        sendPushNotification(finalMsg)
    }

    private suspend fun updateConversation(message: Message) {
        val convId = message.conversationId
        val convRef = db.child("conversations").child(convId)

        val user1 = minOf(message.senderId, message.receiverId)
        val user2 = maxOf(message.senderId, message.receiverId)

        val lastMsgText = when (message.type) {
            MessageType.TEXT -> message.content
            MessageType.LOCATION -> "Đã gửi vị trí"
            MessageType.POST -> "Đã gửi bài đăng"
            else -> message.content
        }

        //lay thong tin cua 2 user
        val user1Info = getUserInfo(user1)
        val user2Info = getUserInfo(user2)

        val updates = mapOf<String, Any?>(
            "conversationId" to convId,
            "user1Id" to user1,
            "user2Id" to user2,
            "user1Name" to user1Info.fullName,
            "user2Name" to user2Info.fullName,
            "user1Avatar" to user1Info.avatar,
            "user2Avatar" to user2Info.avatar,
            "lastMessage" to lastMsgText,
            "lastMessageType" to message.type.name,
            "lastMessageTime" to message.timestamp,
            "lastMessageSenderId" to message.senderId,
            "deleted_${message.senderId}" to 0,
        )

        convRef.updateChildren(updates).await()

        if (message.receiverId != message.senderId) {
            convRef.child("unread_${message.receiverId}")
                .setValue(ServerValue.increment(1)).await()
        }
    }

    // CONVERSATIONS

    fun getConversations(currentUserId: String): Flow<List<Conversation>> = callbackFlow {
        val ref = db.child("conversations")
            .orderByChild("lastMessageTime")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = mutableListOf<Conversation>()

                for (child in snapshot.children) {

                    val conv = child.toBasicConversation(currentUserId)
                        ?: continue

                    if (conv.user1Id != currentUserId &&
                        conv.user2Id != currentUserId
                    ) continue

                    val deletedTime =
                        child.child("deleted_$currentUserId")
                            .getValue(Long::class.java) ?: 0L


                    if (deletedTime == 0L) {
                        conversations.add(conv)
                        continue
                    }

                    val hasNewMessage =
                        conv.lastMessageTime >= deletedTime

                    if (hasNewMessage) {
                        conversations.add(conv)
                    }
                }

                trySend(conversations.sortedByDescending { it.lastMessageTime })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun isDeletedByUser(snapshot: DataSnapshot, userId: String): Boolean {
        return snapshot.child("deleted_$userId").getValue(Boolean::class.java) ?: false
    }

    // Mapper cơ bản
    private fun DataSnapshot.toBasicConversation(currentUserId: String): Conversation? {
        return try {
            val map = value as? Map<*, *> ?: return null
            val convId = map["conversationId"] as? String ?: key ?: ""

            val u1 = map["user1Id"] as? String ?: ""
            val u2 = map["user2Id"] as? String ?: ""
            val otherId = if (u1 == currentUserId) u2 else u1


            val user1Name = map["user1Name"] as? String ?: "Người dùng"
            val user2Name = map["user2Name"] as? String ?: "Người dùng"
            val user1Avatar = map["user1Avatar"] as? String ?: ""
            val user2Avatar = map["user2Avatar"] as? String ?: ""

            Conversation(
                conversationId = convId,
                user1Id = u1,
                user2Id = u2,
                user1Name = user1Name,
                user2Name = user2Name,
                user1Avatar = user1Avatar,
                user2Avatar = user2Avatar,
                lastMessage = map["lastMessage"] as? String ?: "",
                lastMessageType = MessageType.valueOf(
                    map["lastMessageType"] as? String ?: "TEXT"
                ),
                lastMessageTime = (map["lastMessageTime"] as? Number)?.toLong() ?: 0L,
                unreadCount = (map["unread_$currentUserId"] as? Number)?.toInt() ?: 0,
                lastMessageSenderId = map["lastMessageSenderId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }
    // helper

    suspend fun markAsRead(conversationId: String, userId: String) {
        db.child("conversations")
            .child(conversationId)
            .child("unread_$userId")
            .setValue(0).await()
    }

    suspend fun deleteConversation(conversationId: String, userId: String) {
        val convRef = db.child("conversations").child(conversationId)

        val deleteTime = System.currentTimeMillis()

        convRef.child("deleted_$userId")
            .setValue(deleteTime)
            .await()

        val snapshot = convRef.get().await()
        val map = snapshot.value as? Map<*, *> ?: return

        val u1 = map["user1Id"] as? String ?: ""
        val u2 = map["user2Id"] as? String ?: ""

        val otherId = if (u1 == userId) u2 else u1

        val otherDeleted =
            (map["deleted_$otherId"] as? Number)?.toLong() ?: 0L


        if (otherDeleted > 0L) {
            convRef.removeValue().await()
            db.child("messages").child(conversationId).removeValue().await()
        }
    }

    fun getOrCreateConversationId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }


    private fun DataSnapshot.toMessage(): Message? {
        return try {
            val map = value as? Map<*, *> ?: return null
            val locMap = map["location"] as? Map<*, *>
            val postMap = map["post"] as? Map<*, *>

            Message(
                messageId = map["messageId"] as? String ?: key ?: "",
                conversationId = map["conversationId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                receiverId = map["receiverId"] as? String ?: "",
                type = MessageType.valueOf(map["type"] as? String ?: "TEXT"),
                content = map["content"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                isRead = map["isRead"] as? Boolean ?: false,
                location = locMap?.let {
                    MessageLocation(
                        latitude = (it["latitude"] as? Double) ?: 0.0,
                        longitude = (it["longitude"] as? Double) ?: 0.0,
                        address = it["address"] as? String ?: ""
                    )
                },
                post = postMap?.let {
                    MessagePost(
                        postId = it["postId"] as? String ?: "",
                        title = it["title"] as? String ?: "",
                        imageUrl = it["imageUrl"] as? String ?: "",
                        description = it["description"] as? String ?: ""
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Message.toMap(): Map<String, Any?> = mapOf(
        "messageId" to messageId,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "receiverId" to receiverId,
        "type" to type.name,
        "content" to content,
        "timestamp" to timestamp,
        "isRead" to isRead,
        "location" to location?.let {
            mapOf("latitude" to it.latitude, "longitude" to it.longitude, "address" to it.address)
        },
        "post" to post?.let {
            mapOf(
                "postId" to it.postId,
                "title" to it.title,
                "imageUrl" to it.imageUrl,
                "description" to it.description
            )
        }
    )

    private suspend fun sendPushNotification(message: Message) {
        try {
            val receiverSnapshot = db.child("users").child(message.receiverId).get().await()
            val playerId = receiverSnapshot.child("playerId").getValue(String::class.java) ?: return
            val senderName = receiverSnapshot.child("fullName").getValue(String::class.java) ?: "Người dùng"


            val senderSnapshot = db.child("users").child(message.senderId).get().await()
            val senderAvatar = senderSnapshot.child("avatar").getValue(String::class.java) ?: ""
            val senderDisplayName = senderSnapshot.child("fullName").getValue(String::class.java) ?: "Người dùng"

            val bodyText = when (message.type) {
                MessageType.POST -> "Đã gửi bài đăng: ${message.post?.title ?: ""}"
                MessageType.LOCATION -> "Đã gửi vị trí"
                else -> message.content.take(120)
            }

            saveNotification(
                Notification(
                    userId = message.receiverId,
                    type = "message",
                    title = senderDisplayName,
                    content = bodyText,
                    senderId = message.senderId,
                    senderName = senderDisplayName,
                    senderAvatar = senderAvatar,
                    conversationId = message.conversationId
                )
            )

            val json = JSONObject().apply {
                put("app_id", "bdf5516d-cd73-4dd2-b189-c429f8311bd5")
                put("include_player_ids", JSONArray().put(playerId))
                put("headings", JSONObject().put("en", senderDisplayName))
                put("contents", JSONObject().put("en", bodyText))
                put("priority", 10)
                put("small_icon", "ic_notification")
                put("data", JSONObject().apply {
                    put("conversationId", message.conversationId)
                    put("otherUserId", message.senderId)
                    put("otherUserName", senderDisplayName)
                    put("type", "chat_message")
                })
            }

            withContext(Dispatchers.IO) {
                val body = RequestBody.create(
                    "application/json; charset=utf-8".toMediaType(), json.toString()
                )
                val request = Request.Builder()
                    .url("https://onesignal.com/api/v1/notifications")
                    .post(body)
                    .addHeader("Authorization", "Key os_v2_app_xx2vc3onong5fmmjyqu7qmi32uibwfrtofleignbzmxd4mhhltg7t6n6w4qisvt7g6vrstfdujwzmncg2dmnfx6pp6uyzv45iededey")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .build()
                val response = client.newCall(request).execute()
                android.util.Log.d("OneSignal", "Push response: ${response.code}")
            }

        } catch (e: Exception) {
            android.util.Log.e("OneSignal", "Push failed", e)
        }
    }

    private suspend fun saveNotification(notification: Notification) {
        try {
            val ref = db.child("notifications").child(notification.userId)
            val notiId = ref.push().key ?: return
            val finalNoti = notification.copy(id = notiId)
            ref.child(notiId).setValue(finalNoti.toMap()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}