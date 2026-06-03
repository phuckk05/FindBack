package com.app.findback

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.app.findback.databinding.ActivityWelcomeBinding
import com.app.findback.ui.activities.BaseBottomNavActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasSeenWelcome = prefs.getBoolean("has_seen_welcome", false)
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            goTo(BaseBottomNavActivity::class.java)
            return
        }


        if (hasSeenWelcome) {
            goTo(LoginActivity::class.java)
            return
        }


        prefs.edit().putBoolean("has_seen_welcome", true).apply()

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeadline()
        setupLoginLink()

        binding.btnStart.setOnClickListener {
            goTo(LoginActivity::class.java)
        }
    }

    private fun goTo(cls: Class<*>) {
        startActivity(
            Intent(this, cls).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun setupHeadline() {
        val text = "Tìm lại đồ thất lạc,\nKết nối cộng đồng"
        val spannable = SpannableString(text)
        val blueStart = text.indexOf("Kết nối")
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#1565F9")),
            blueStart,
            text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvHeadline.text = spannable
    }

    private fun setupLoginLink() {
        val fullText = "Bạn đã có tài khoản? Đăng nhập"
        val spannable = SpannableStringBuilder(fullText)
        val clickStart = fullText.indexOf("Đăng nhập")
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    goTo(LoginActivity::class.java)
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Color.parseColor("#1565F9")
                    ds.isUnderlineText = false
                }
            },
            clickStart,
            fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvLoginLink.text = spannable
        binding.tvLoginLink.movementMethod = LinkMovementMethod.getInstance()
        binding.tvLoginLink.highlightColor = Color.TRANSPARENT
    }
}