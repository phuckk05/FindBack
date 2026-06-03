package com.app.findback.domain.models

data class Notification(
    val id: String = "",
    val userId: String = "",
    val type: String = "message",
    val title: String = "",
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val conversationId: String = "",
    val postId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "type" to type,
        "title" to title,
        "content" to content,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderAvatar" to senderAvatar,
        "conversationId" to conversationId,
        "postId" to postId,
        "timestamp" to timestamp,
        "isRead" to isRead
    )
}