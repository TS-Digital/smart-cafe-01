package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.databinding.ActivityRegisterBinding
import com.p2897777.ctec3703spec.util.Validators

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private val vm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toLogin.setOnClickListener { finish() }
        b.registerButton.setOnClickListener { submit() }

        vm.state.observe(this) { state ->
            val loading = state is AuthViewModel.State.Loading
            b.progress.visibility = if (loading) View.VISIBLE else View.GONE
            b.registerButton.isEnabled = !loading
            when (state) {
                is AuthViewModel.State.LoggedIn -> {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is AuthViewModel.State.Error -> Snackbar.make(b.root, state.message, Snackbar.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    private fun submit() {
        val name = b.nameInput.text?.toString().orEmpty()
        val email = b.emailInput.text?.toString().orEmpty()
        val password = b.passwordInput.text?.toString().orEmpty()
        val confirm = b.confirmInput.text?.toString().orEmpty()
        b.nameLayout.error = Validators.name(name)
        b.emailLayout.error = Validators.email(email)
        b.passwordLayout.error = Validators.password(password)
        b.confirmLayout.error = Validators.confirmPassword(password, confirm)
        val valid = listOf(b.nameLayout, b.emailLayout, b.passwordLayout, b.confirmLayout).all { it.error == null }
        if (valid) vm.register(name, email, password)
    }
}
