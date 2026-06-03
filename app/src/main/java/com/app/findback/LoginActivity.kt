package com.app.findback

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.app.findback.databinding.ActivityLoginBinding
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()
        setupListeners()
        setupRegisterLink()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { login() }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent (this, ForgotPasswordActivity::class.java))
        }
    }

    private fun login() {

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) { binding.etEmail.error = "Vui lòng nhập email"; return }
        if (password.isEmpty()) { binding.etPassword.error = "Vui lòng nhập mật khẩu"; return }
        if (password.length < 6) { binding.etPassword.error = "Mật khẩu tối thiểu 6 ký tự"; return }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    // ...
                } else {
                    // Log chi tiết lỗi
                    android.util.Log.e("AUTH_ERROR", "Exception: ${task.exception}")
                    android.util.Log.e("AUTH_ERROR", "Message: ${task.exception?.message}")
                    android.util.Log.e("AUTH_ERROR", "Cause: ${task.exception?.cause}")
                    Toast.makeText(this, task.exception?.message ?: "Lỗi", Toast.LENGTH_LONG).show()
                }
            }
        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)

                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                    // Luu playerId sau khi login thanh cong
                    savePlayerIdToFirebase(uid)

                    Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, BaseBottomNavActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Đăng nhập thất bại",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun savePlayerIdToFirebase(uid: String) {
        val userRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("playerId")


        val currentId = OneSignal.User.pushSubscription.id
        if (!currentId.isNullOrEmpty()) {
            userRef.setValue(currentId)
            android.util.Log.d("OneSignal", "Saved playerId immediately: $currentId")
            return
        }


        CoroutineScope(Dispatchers.IO).launch {
            repeat(10) { attempt ->
                delay(500)
                val id = OneSignal.User.pushSubscription.id
                if (!id.isNullOrEmpty()) {
                    userRef.setValue(id)
                    android.util.Log.d("OneSignal", "Saved playerId after ${(attempt+1)*500}ms: $id")
                    return@launch
                }
            }
            android.util.Log.e("OneSignal", "Could not get playerId after 5s")
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }
    private fun setupRegisterLink() {
        val full = "Chưa có tài khoản? Đăng ký ngay"
        val spannable = SpannableStringBuilder(full)
        val start = full.indexOf("Đăng ký ngay")
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(w: View) {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#1565F9")
                ds.isUnderlineText = false
            }
        }, start, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvRegister.text = spannable
        binding.tvRegister.movementMethod = LinkMovementMethod.getInstance()
        binding.tvRegister.highlightColor = Color.TRANSPARENT
    }
}