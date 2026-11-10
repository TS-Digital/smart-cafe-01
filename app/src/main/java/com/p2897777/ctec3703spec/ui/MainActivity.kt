package com.p2897777.ctec3703spec.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.p2897777.ctec3703spec.R
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hosts the four customer tabs: Menu, Cart, Orders, Account. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Snackbar.make(b.root, "Notifications are off. You can still track orders in the Orders tab.", Snackbar.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Access control: every screen behind login checks the session
        if (!app.session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.subtitle = "Hi, ${app.session.userName.substringBefore(' ')}"

        b.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            val start = intent.getIntExtra(EXTRA_TAB, R.id.nav_menu)
            b.bottomNav.selectedItemId = start
        }

        DataEvents.cartChanged.observe(this) { refreshCartBadge() }
        askForNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) refreshCartBadge()
    }

    fun selectTab(id: Int) {
        b.bottomNav.selectedItemId = id
    }

    private fun show(itemId: Int) {
        val tag = itemId.toString()
        val existing = supportFragmentManager.findFragmentByTag(tag)
        val fragment: Fragment = existing ?: when (itemId) {
            R.id.nav_cart -> CartFragment()
            R.id.nav_orders -> OrdersFragment()
            R.id.nav_account -> AccountFragment()
            else -> MenuFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }

    private fun refreshCartBadge() {
        val userId = app.session.userId
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { app.cart.itemCount(userId) }
            val badge = b.bottomNav.getOrCreateBadge(R.id.nav_cart)
            badge.isVisible = count > 0
            badge.number = count
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
