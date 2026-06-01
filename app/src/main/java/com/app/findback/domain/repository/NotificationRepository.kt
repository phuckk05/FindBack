package com.app.findback.domain.repository

import com.app.findback.domain.models.Notification
import com.app.findback.domain.models.toMap
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {

    private val db = FirebaseDatabase.getInstance().reference

    fun getUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val ref = db.child("notifications").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count { child ->
                    child.child("isRead").getValue(Boolean::class.java) == false
                }
                trySend(count)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(0)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getNotifications(userId: String): Flow<List<Notification>> = callbackFlow {
        val ref = db.child("notifications").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    it.getValue(Notification::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun markAllAsRead(userId: String) {
        try {
            val ref = db.child("notifications").child(userId)
            val snapshot = ref.get().await()
            val updates = mutableMapOf<String, Any>()
            snapshot.children.forEach { child ->
                updates["${child.key}/isRead"] = true
            }
            if (updates.isNotEmpty()) ref.updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAsRead(userId: String, notificationId: String) {
        try {
            db.child("notifications").child(userId)
                .child(notificationId)
                .child("isRead")
                .setValue(true)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteNotification(userId: String, notificationId: String) {
        FirebaseDatabase.getInstance()
            .getReference("notifications")
            .child(userId)
            .child(notificationId)
            .removeValue()
            .await()
    }

    suspend fun restoreNotification(userId: String, notification: Notification) {
        FirebaseDatabase.getInstance()
            .getReference("notifications")
            .child(userId)
            .child(notification.id)
            .setValue(notification)
            .await()
    }

}