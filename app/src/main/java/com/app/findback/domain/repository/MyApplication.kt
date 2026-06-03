package com.app.findback

import android.app.Application
import android.content.Intent
import com.app.findback.PopUpNotifications.AppActivityTracker
import com.app.findback.PopUpNotifications.InAppNotificationManager
import com.app.findback.data.repositories.CloudinaryManager
import com.app.findback.domain.models.Notification
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.app.findback.ui.activities.ChatActivity
import com.app.findback.ui.activities.PostDetailActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.initialize
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import org.json.JSONObject

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.google.firebase.Firebase.initialize(this)
        CloudinaryManager.init(this)

        registerActivityLifecycleCallbacks(AppActivityTracker)

        OneSignal.initWithContext(this, "bdf5516d-cd73-4dd2-b189-c429f8311bd5")

        setupOneSignalListeners()
    }

    private fun setupOneSignalListeners() {


        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                val osNotification = event.notification
                val data = osNotification.additionalData ?: return

                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (currentUserId.isBlank()) return

                val type = data.optString("type", "")


                if (type == "new_comment") {
                    val postId = data.optString("postId", "")
                    if (postId.isNotEmpty()) {
                        event.preventDefault()
                        val activity = AppActivityTracker.activeActivity
                        activity?.runOnUiThread {
                            InAppNotificationManager.show(
                                activity = activity,
                                notification = createCommentNotification(osNotification, data)
                            ) {
                                openPostWithComment(activity, postId)
                            }
                        }
                    }
                    return
                }


                val otherUserId = data.optString("otherUserId", "")
                if (otherUserId == currentUserId) {
                    event.preventDefault()
                    return
                }

                val conversationId = data.optString("conversationId", "")
                val activity = AppActivityTracker.activeActivity
                if (activity is ChatActivity && activity.getCurrentConversationId() == conversationId) {
                    event.preventDefault()
                    return
                }

                event.preventDefault()

                val senderName = data.optString("otherUserName", "")
                    .ifEmpty { osNotification.title ?: "Người dùng" }

                val notificationModel = Notification(
                    type = "message",
                    title = senderName,
                    content = osNotification.body ?: "",
                    senderName = senderName,
                    conversationId = conversationId,
                    senderId = otherUserId,
                    senderAvatar = ""
                )

                activity?.runOnUiThread {
                    InAppNotificationManager.show(
                        activity = activity,
                        notification = notificationModel
                    ) { n: Notification ->
                        val intent = Intent(activity, ChatActivity::class.java).apply {
                            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, n.conversationId)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, n.senderId)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, n.senderName)
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        activity.startActivity(intent)
                    }
                }
            }
        })

        // Click listener (Bấm vào thông báo)
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData ?: return
                val type = data.optString("type", "")

                if (type == "new_comment") {
                    val postId = data.optString("postId", "")
                    if (postId.isNotEmpty()) {
                        openPostWithComment(null, postId)
                        return
                    }
                }

                // Logic chat cũ
                val conversationId = data.optString("conversationId", "")
                val otherUserId = data.optString("otherUserId", "")
                val otherUserName = data.optString("otherUserName", "")

                val activeActivity = AppActivityTracker.activeActivity

                val intent = Intent(activeActivity ?: applicationContext, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(ChatActivity.EXTRA_OTHER_USER_ID, otherUserId)
                    putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, otherUserName)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                if (activeActivity != null) {
                    activeActivity.startActivity(intent)
                } else {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    applicationContext.startActivity(intent)
                }
            }
        })
    }

    private fun createCommentNotification(osNotification: com.onesignal.notifications.INotification, data: org.json.JSONObject): Notification {
        return Notification(
            type = "new_comment",
            title = data.optString("senderName", osNotification.title ?: "Người dùng"),
            content = osNotification.body ?: "",
            senderName = data.optString("senderName", ""),
            postId = data.optString("postId", ""),
            senderId = data.optString("senderId", "")
        )
    }

    private fun openPostWithComment(activity: android.app.Activity?, postId: String) {
        val intent = Intent(activity ?: applicationContext, BaseBottomNavActivity::class.java).apply {  // ← Đổi thành BaseBottomNavActivity
            putExtra("targetPostId", postId)
            putExtra("scrollToComment", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (activity != null) {
            activity.startActivity(intent)
        } else {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            applicationContext.startActivity(intent)
        }
    }
}