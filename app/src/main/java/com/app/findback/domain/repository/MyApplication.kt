package com.app.findback

import android.app.Application
import android.content.Intent
import com.app.findback.PopUpNotifications.AppActivityTracker
import com.app.findback.PopUpNotifications.InAppNotificationManager
import com.app.findback.data.repositories.CloudinaryManager
import com.app.findback.domain.models.Notification
import com.app.findback.ui.activities.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        CloudinaryManager.init(this)

        // Đăng ký AppActivityTracker ở đây để theo dõi activity active
        registerActivityLifecycleCallbacks(AppActivityTracker)

        OneSignal.initWithContext(this, "bdf5516d-cd73-4dd2-b189-c429f8311bd5")

        setupOneSignalListeners()
    }

    private fun setupOneSignalListeners() {

        // Foreground: chặn notification hệ thống, hiện in-app banner thay thế
        OneSignal.Notifications.addForegroundLifecycleListener(object :
            INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                val osNotification = event.notification
                val data = osNotification.additionalData ?: run {
                    // Không có data → cho hệ thống hiện bình thường
                    return
                }

                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (currentUserId.isBlank()) return

                val otherUserId = data.optString("otherUserId", "")

                // Notification do chính mình gửi → bỏ qua
                if (otherUserId == currentUserId) {
                    event.preventDefault()
                    return
                }

                val conversationId = data.optString("conversationId", "")

                // Nếu đang ở đúng ChatActivity của conversation này → không hiện gì hết
                val activity = AppActivityTracker.activeActivity
                if (activity is ChatActivity && activity.getCurrentConversationId() == conversationId) {
                    event.preventDefault()
                    return
                }

                // Chặn notification hệ thống, hiện in-app banner
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

        // Background/killed: user ấn notification → mở ChatActivity
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData ?: return

                val conversationId = data.optString("conversationId", "")
                val otherUserId = data.optString("otherUserId", "")
                val otherUserName = data.optString("otherUserName", "")

                // Dùng AppActivityTracker để kiểm tra app có đang chạy không
                val activeActivity = AppActivityTracker.activeActivity

                if (activeActivity != null) {
                    // App đang foreground → mở ChatActivity từ activity hiện tại
                    val intent = Intent(activeActivity, ChatActivity::class.java).apply {
                        putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(ChatActivity.EXTRA_OTHER_USER_ID, otherUserId)
                        putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, otherUserName)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    activeActivity.startActivity(intent)
                } else {
                    // App đang background/killed → mở qua Application context
                    val intent = Intent(applicationContext, ChatActivity::class.java).apply {
                        putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(ChatActivity.EXTRA_OTHER_USER_ID, otherUserId)
                        putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, otherUserName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    applicationContext.startActivity(intent)
                }
            }
        })
    }
}