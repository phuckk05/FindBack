package com.app.findback

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.app.findback.PopUpNotifications.AppActivityTracker
import com.app.findback.PopUpNotifications.InAppNotificationManager
import com.app.findback.databinding.ActivityMainBinding
import com.app.findback.domain.models.Notification
import com.app.findback.ui.NotificationHelper
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.app.findback.ui.activities.ChatActivity
import com.google.firebase.auth.FirebaseAuth
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)

        application.registerActivityLifecycleCallbacks(AppActivityTracker)

        setupOneSignal()
        setToolbar()
        navigateToMainScreen()
    }

    private fun setupOneSignal() {
        OneSignal.initWithContext(this, "bdf5516d-cd73-4dd2-b189-c429f8311bd5")

        OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
            override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                val osNotification = event.notification
                val activity = AppActivityTracker.activeActivity ?: return

                val data = osNotification.additionalData ?: run {
                    event.preventDefault()
                    return
                }

                val currentUserId = getCurrentUserId()
                if (currentUserId.isBlank()) {
                    event.preventDefault()
                    return
                }

                val otherUserId = data.optString("otherUserId", "")


                if (otherUserId == currentUserId) {
                    event.preventDefault()
                    return
                }

                event.preventDefault()

                val conversationId = data.optString("conversationId", "")
                val senderId = otherUserId
                val senderName = data.optString("otherUserName", "")
                    ?: osNotification.title ?: "Người dùng"

                val notificationModel = Notification(
                    type = "message",
                    title = senderName,
                    content = osNotification.body ?: "",
                    senderName = senderName,
                    conversationId = conversationId,
                    senderId = senderId,
                    senderAvatar = ""
                )

                activity.runOnUiThread {
                    InAppNotificationManager.show(
                        activity = activity,
                        notification = notificationModel
                    ) { n: Notification ->
                        val intent = Intent(activity, ChatActivity::class.java).apply {
                            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, n.conversationId)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, n.senderId)
                            putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, n.senderName)
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        activity.startActivity(intent)
                    }
                }
            }
        })

        // Click listener giữ nguyên
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData ?: return

                val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CONVERSATION_ID, data.optString("conversationId", ""))
                    putExtra(ChatActivity.EXTRA_OTHER_USER_ID, data.optString("otherUserId", ""))
                    putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, data.optString("otherUserName", ""))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        })

        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: ""
    }

    private fun navigateToMainScreen() {
        startActivity(Intent(this, BaseBottomNavActivity::class.java))
        finish()
    }

    private fun setToolbar() {
        setupToolbarCus(
            binding.toolbarLayout.toolbar,
            "Goc tim do",
            false,
            null,
            false,
            R.drawable.logo_tran,
            R.drawable.ic_notification,
            R.drawable.ic_search
        )
    }
}