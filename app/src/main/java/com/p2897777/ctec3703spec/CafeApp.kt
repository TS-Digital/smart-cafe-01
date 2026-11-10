package com.p2897777.ctec3703spec

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.p2897777.ctec3703spec.data.CafeDatabase
import com.p2897777.ctec3703spec.data.CartRepository
import com.p2897777.ctec3703spec.data.FeedbackRepository
import com.p2897777.ctec3703spec.data.OrderRepository
import com.p2897777.ctec3703spec.data.ProductRepository
import com.p2897777.ctec3703spec.data.UserRepository
import com.p2897777.ctec3703spec.notify.OrderNotifier
import com.p2897777.ctec3703spec.predict.WaitTimeService
import com.p2897777.ctec3703spec.sim.DemoDataGenerator
import com.p2897777.ctec3703spec.recommend.WeatherService
import com.p2897777.ctec3703spec.sim.OrderSimulator
import com.p2897777.ctec3703spec.recommend.RecommendationStats
import com.p2897777.ctec3703spec.util.AppSettings
import com.p2897777.ctec3703spec.util.LoginRateLimiter
import com.p2897777.ctec3703spec.util.SessionManager

/**
 * Application class doubling as a small manual dependency container.
 * Every screen gets the same repository instances from here, which keeps
 * construction in one place and makes the dependencies easy to explain.
 */
class CafeApp : Application() {

    lateinit var session: SessionManager private set
    lateinit var users: UserRepository private set
    lateinit var products: ProductRepository private set
    lateinit var cart: CartRepository private set
    lateinit var orders: OrderRepository private set
    lateinit var feedback: FeedbackRepository private set
    lateinit var notifier: OrderNotifier private set
    lateinit var weather: WeatherService private set
    lateinit var simulator: OrderSimulator private set
    lateinit var waitTimes: WaitTimeService private set
    lateinit var demoData: DemoDataGenerator private set
    lateinit var settings: AppSettings private set
    lateinit var recStats: RecommendationStats private set

    override fun onCreate() {
        super.onCreate()
        val db = CafeDatabase.get(this)
        session = SessionManager(this)
        users = UserRepository(db, limiter = LoginRateLimiter(LoginRateLimiter.PrefsStore(this)))
        products = ProductRepository(db)
        cart = CartRepository(db, products)
        orders = OrderRepository(db, cart)
        feedback = FeedbackRepository(db, orders)
        weather = WeatherService(this)
        waitTimes = WaitTimeService(orders)
        demoData = DemoDataGenerator(db, products)
        settings = AppSettings(this)
        recStats = RecommendationStats(RecommendationStats.PrefsStore(this))
        notifier = OrderNotifier(this).also { it.createChannel() }

        val prefs = getSharedPreferences("simulator", MODE_PRIVATE)
        simulator = OrderSimulator(orders, onUpdate = { notifier.notifyStatus(it, session.userId) }).apply {
            enabled = prefs.getBoolean("enabled", true)
            speedFactor = prefs.getFloat("speed", 1f).toDouble()
        }
        // Runs while the app process is alive, simulating the café kitchen working through orders
        simulator.start(ProcessLifecycleOwner.get().lifecycleScope)
    }

    fun saveSimulatorSettings(enabled: Boolean, speed: Double) {
        simulator.enabled = enabled
        simulator.speedFactor = speed
        getSharedPreferences("simulator", MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putFloat("speed", speed.toFloat()).apply()
    }
}

val android.content.Context.app: CafeApp get() = applicationContext as CafeApp
