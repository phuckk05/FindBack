package com.app.findback

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.app.findback.databinding.ActivityRegisterBinding
import com.app.findback.domain.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference
    private val client = OkHttpClient()

    // ========== EMAILJS ==========
    private val SERVICE_ID = "service_3v7um35"
    private val TEMPLATE_ID = "template_at9xpp9"
    private val PUBLIC_KEY = "afe6Bpob5V-3FRdZa"
    private val PRIVATE_KEY = "YOUR_EMAILJS_PRIVATE_KEY" // Lấy tại: EmailJS Dashboard → Account → Private Key
    // ==============================

    private var generatedOtp: String = ""
    private var otpExpiryTime: Long = 0L
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupListeners()
        setupLoginLink()
    }

    private fun setupListeners() {
        binding.btnSendCode.setOnClickListener { sendOtp() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener { finish() }
    }

    // ==================== BƯỚC 1: GỬI OTP QUA EMAILJS ====================
    private fun sendOtp() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Vui lòng nhập email hợp lệ"
            return
        }

        generatedOtp = (100000..999999).random().toString()
        otpExpiryTime = System.currentTimeMillis() + 5 * 60 * 1000

        showLoading(true)

        val jsonBody = JSONObject().apply {
            put("service_id", SERVICE_ID)
            put("template_id", TEMPLATE_ID)
            put("user_id", PUBLIC_KEY)
            put("accessToken", PRIVATE_KEY) // Fix: dùng private key thay vì origin header
            put("template_params", JSONObject().apply {
                put("to_email", email)
                put("otp_code", generatedOtp)
            })
        }

        val request = Request.Builder()
            .url("https://api.emailjs.com/api/v1.0/email/send")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .header("origin", "http://localhost")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@RegisterActivity, "Gửi OTP thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    showLoading(false)
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Đã gửi mã OTP đến $email!\nMã có hiệu lực trong 5 phút.",
                            Toast.LENGTH_LONG
                        ).show()
                        startCountDown()
                    } else {
                        val errorBody = response.body?.string()
                        Toast.makeText(
                            this@RegisterActivity,
                            "Gửi OTP thất bại: $errorBody",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    // ==================== ĐẾM NGƯỢC 60 GIÂY ====================
    private fun startCountDown() {
        binding.btnSendCode.isEnabled = false
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnSendCode.text = "Gửi lại (${seconds}s)"
            }

            override fun onFinish() {
                binding.btnSendCode.isEnabled = true
                binding.btnSendCode.text = "Gửi mã"
            }
        }.start()
    }

    // ==================== BƯỚC 2: XÁC NHẬN OTP + ĐĂNG KÝ ====================
    private fun attemptRegister() {

        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val inputOtp = binding.etOtp.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Nhập họ tên"
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Nhập email"
            return
        }

        if (phone.isEmpty()) {
            binding.etPhone.error = "Nhập số điện thoại"
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Mật khẩu phải có ít nhất 6 ký tự"
            return
        }

        if (inputOtp.length != 6) {
            binding.etOtp.error = "Nhập mã OTP 6 số"
            return
        }

        when {
            generatedOtp.isEmpty() -> {
                Toast.makeText(this, "Vui lòng nhấn 'Gửi mã' trước", Toast.LENGTH_SHORT).show()
                return
            }

            System.currentTimeMillis() > otpExpiryTime -> {
                Toast.makeText(this, "Mã OTP đã hết hạn", Toast.LENGTH_SHORT).show()
                return
            }

            inputOtp != generatedOtp -> {
                binding.etOtp.error = "Mã OTP không đúng"
                return
            }
        }

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    val uid = auth.currentUser!!.uid
                    saveUserToDatabase(uid, fullName, email, phone)
                } else {
                    showLoading(false)

                    when (task.exception) {
                        is FirebaseAuthUserCollisionException ->
                            binding.etEmail.error = "Email đã tồn tại"

                        is FirebaseAuthWeakPasswordException ->
                            binding.etPassword.error = "Mật khẩu quá yếu"

                        else ->
                            Toast.makeText(this, task.exception?.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun saveUserToDatabase(
        uid: String,
        fullName: String,
        email: String,
        phone: String
    ) {

        val user = User(
            uid = uid,
            fullName = fullName,
            email = email,
            phoneNumber = phone
        )

        database.child("users")
            .child(uid)
            .setValue(user)
            .addOnSuccessListener {
                showLoading(false)
                generatedOtp = ""

                Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()

                auth.signOut()

                startActivity(
                    android.content.Intent(this, LoginActivity::class.java)
                )
                finish()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                showLoading(false)
                Toast.makeText(this, "Lưu thất bại: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        if (!isLoading) binding.btnSendCode.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
    private fun setupLoginLink() {
        val full = "Đã có tài khoản? Đăng nhập"
        val spannable = SpannableStringBuilder(full)
        val start = full.indexOf("Đăng nhập")
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(w: View) {
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#1565F9")
                ds.isUnderlineText = false
            }
        }, start, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvLogin.text = spannable
        binding.tvLogin.movementMethod = LinkMovementMethod.getInstance()
        binding.tvLogin.highlightColor = Color.TRANSPARENT
    }
}