package com.app.findback

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.findback.databinding.ActivityMainBinding
import com.app.findback.ui.NotificationHelper
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val firebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val REQUEST_CODE_NOTIFICATION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)

        // OneSignal đã init trong MyApplication, chỉ cần save ID ở đây
        saveOneSignalId()
        saveFcmToken()
        setToolbar()

        // Request permission TRƯỚC, navigate bên trong callback
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            navigateToMainScreen()
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                navigateToMainScreen()
            }

            !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                AlertDialog.Builder(this)
                    .setTitle("Cần bật thông báo")
                    .setMessage("Thông báo đang bị tắt. Vui lòng vào Cài đặt → Thông báo và bật lên để nhận tin nhắn.")
                    .setPositiveButton("Mở Cài đặt") { _, _ ->
                        openAppSettings()
                        navigateToMainScreen()
                    }
                    .setNegativeButton("Bỏ qua") { _, _ -> navigateToMainScreen() }
                    .setCancelable(false)
                    .show()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                AlertDialog.Builder(this)
                    .setTitle("Cần quyền thông báo")
                    .setMessage("Ứng dụng cần quyền thông báo để bạn nhận được tin nhắn kịp thời.")
                    .setPositiveButton("Đồng ý") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_NOTIFICATION
                        )
                    }
                    .setNegativeButton("Từ chối") { _, _ -> navigateToMainScreen() }
                    .setCancelable(false)
                    .show()
            }

            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_NOTIFICATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                navigateToMainScreen()
            } else {
                val permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.POST_NOTIFICATIONS
                )
                if (permanentlyDenied) {
                    AlertDialog.Builder(this)
                        .setTitle("Thông báo bị tắt")
                        .setMessage("Bạn đã tắt quyền thông báo. Vui lòng vào Cài đặt để bật lại thủ công.")
                        .setPositiveButton("Mở Cài đặt") { _, _ ->
                            openAppSettings()
                            navigateToMainScreen()
                        }
                        .setNegativeButton("Bỏ qua") { _, _ -> navigateToMainScreen() }
                        .setCancelable(false)
                        .show()
                } else {
                    navigateToMainScreen()
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
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

    private fun saveOneSignalId() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val subscriptionId = OneSignal.User.pushSubscription.id
        Log.d("ONESIGNAL_DEBUG", "saveOneSignalId = $subscriptionId")
        if (!subscriptionId.isNullOrEmpty()) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("playerId")
                .setValue(subscriptionId)
                .addOnSuccessListener { Log.d("ONESIGNAL_DEBUG", "Saved to Firebase") }
        }
    }

    private fun saveFcmToken() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("fcmToken")
                .setValue(token)
        }
    }
}