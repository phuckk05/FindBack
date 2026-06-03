package com.app.findback.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val content: String = "",
    val createdAt: Long = 0L
) : Parcelable {

    fun toMap(): Map<String, Any?> = mapOf(
        "commentId" to commentId,
        "postId" to postId,
        "userId" to userId,
        "userName" to userName,
        "userAvatar" to userAvatar,
        "content" to content,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Comment = Comment(
            commentId = map["commentId"] as? String ?: "",
            postId = map["postId"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userAvatar = map["userAvatar"] as? String ?: "",
            content = map["content"] as? String ?: "",
            createdAt = map["createdAt"] as? Long ?: 0L
        )
    }
}