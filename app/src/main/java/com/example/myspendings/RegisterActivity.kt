package com.example.myspendings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // IDs from activity_register.xml
        // NOTE: etUsername now holds the user's EMAIL ADDRESS (used as their Firebase login)
        val etUsername        = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword        = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnRegister       = findViewById<MaterialButton>(R.id.btnRegister)
        val tvLogin           = findViewById<TextView>(R.id.tvLogin)

        btnRegister.setOnClickListener {
            val email    = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm  = etConfirmPassword.text.toString().trim()

            // Validation
            if (email.isEmpty()) {
                etUsername.error = getString(R.string.error_username_empty); return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etUsername.error = "Please enter a valid email address"; return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = getString(R.string.error_password_empty); return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"; return@setOnClickListener
            }
            if (password != confirm) {
                etConfirmPassword.error = getString(R.string.error_passwords_no_match); return@setOnClickListener
            }

            btnRegister.isEnabled = false

            // ── Firebase Authentication: create the account ─────────────
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val firebaseUid  = firebaseUser?.uid ?: ""

                        // ── Mirror the Firebase account into the local Room DB ──
                        // so existing expense/category/budget tables (which key off
                        // an Int userId) keep working unchanged.
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.userDao().insert(User(username = email, firebaseUid = firebaseUid))
                            withContext(Dispatchers.Main) {
                                btnRegister.isEnabled = true
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Account created! Please log in.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    } else {
                        btnRegister.isEnabled = true
                        Toast.makeText(
                            this@RegisterActivity,
                            task.exception?.message ?: "Registration failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        tvLogin.setOnClickListener { finish() }
    }
}