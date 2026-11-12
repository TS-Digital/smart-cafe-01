package com.p2897777.ctec3703spec.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.databinding.ActivityLoginBinding
import com.p2897777.ctec3703spec.util.Validators

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val vm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Already logged in: skip straight to the app
        if (app.session.isLoggedIn) {
            goToMain()
            return
        }
        // The coffee banner runs up behind the status bar, so draw edge-to-edge with light status icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        val bannerTop = b.banner.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            b.banner.updatePadding(top = bannerTop + bars.top)
            b.content.updatePadding(left = bars.left, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }

        b.loginButton.setOnClickListener { submit() }
        b.passwordInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        b.toRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }

        vm.state.observe(this) { state ->
            val loading = state is AuthViewModel.State.Loading
            b.progress.visibility = if (loading) View.VISIBLE else View.GONE
            b.loginButton.isEnabled = !loading
            when (state) {
                is AuthViewModel.State.LoggedIn -> goToMain()
                is AuthViewModel.State.Error -> Snackbar.make(b.root, state.message, Snackbar.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    private fun submit() {
        val email = b.emailInput.text?.toString().orEmpty()
        val password = b.passwordInput.text?.toString().orEmpty()
        b.emailLayout.error = Validators.email(email)
        b.passwordLayout.error = if (password.isEmpty()) "Please enter your password" else null
        if (b.emailLayout.error == null && b.passwordLayout.error == null) vm.login(email, password)
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
