package com.app.findback.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.findback.domain.models.Notification
import com.app.findback.domain.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {

    private val repository = NotificationRepository()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    init {
        loadNotifications()
        observeUnreadCount()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            repository.getNotifications(currentUserId).collect {
                _notifications.value = it
            }
        }
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            repository.getUnreadCount(currentUserId).collect {
                _unreadCount.value = it
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead(currentUserId)
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markAsRead(currentUserId, notificationId)
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotification(currentUserId, notificationId)
        }
    }

    fun restoreNotification(notification: Notification) {
        viewModelScope.launch {
            repository.restoreNotification(currentUserId, notification)
        }
    }
}