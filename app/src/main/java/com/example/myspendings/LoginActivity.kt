package com.example.myspendings

import android.content.Intent
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

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // If already logged in (both Firebase session AND local session) skip straight to MainActivity
        if (auth.currentUser != null && SessionManager.isLoggedIn(this)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        // IDs from activity_login.xml
        // NOTE: etUsername now holds the user's EMAIL ADDRESS (used as their Firebase login)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin   = findViewById<MaterialButton>(R.id.btnLogin)
        val tvSignUp   = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener {
            val email    = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etUsername.error = getString(R.string.error_username_empty)
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = getString(R.string.error_password_empty)
                return@setOnClickListener
            }

            btnLogin.isEnabled = false

            // ── Firebase Authentication: sign in ─────────────────────────
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUid = auth.currentUser?.uid ?: ""

                        // ── Map the Firebase user to the local Room userId ──
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            var localUser = db.userDao().findByFirebaseUid(firebaseUid)

                            // Safety net: if a Firebase account exists but has no
                            // local Room record yet (e.g. created on another device),
                            // create one now so expenses/categories have a userId.
                            if (localUser == null) {
                                db.userDao().insert(User(username = email, firebaseUid = firebaseUid))
                                localUser = db.userDao().findByFirebaseUid(firebaseUid)
                            }

                            withContext(Dispatchers.Main) {
                                btnLogin.isEnabled = true
                                if (localUser != null) {
                                    SessionManager.saveUser(applicationContext, localUser.id, localUser.username)
                                    goToMain()
                                } else {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Could not set up local profile. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        btnLogin.isEnabled = true
                        Toast.makeText(
                            this@LoginActivity,
                            task.exception?.message ?: getString(R.string.error_invalid_credentials),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}