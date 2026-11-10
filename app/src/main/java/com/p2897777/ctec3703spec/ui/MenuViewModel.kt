package com.p2897777.ctec3703spec.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.CartRepository
import com.p2897777.ctec3703spec.data.DataEvents
import com.p2897777.ctec3703spec.model.Category
import com.p2897777.ctec3703spec.model.Product
import com.p2897777.ctec3703spec.recommend.RecommendationEngine
import com.p2897777.ctec3703spec.recommend.WeatherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MenuViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.app

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _ratings = MutableLiveData<Map<Long, Pair<Double, Int>>>(emptyMap())
    val ratings: LiveData<Map<Long, Pair<Double, Int>>> = _ratings

    private val _recs = MutableLiveData<List<RecommendationEngine.Recommendation>>(emptyList())
    val recommendations: LiveData<List<RecommendationEngine.Recommendation>> = _recs

    private val _weatherLine = MutableLiveData("")
    val weatherLine: LiveData<String> = _weatherLine

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    var query = ""; private set
    var category: Category? = null; private set
    var availableOnly = false; private set

    private var searchJob: Job? = null

    fun setQuery(q: String) {
        if (q == query) return
        query = q
        // Debounce typing so we don't hit the database on every keystroke
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(250); loadMenu() }
    }

    fun setCategory(c: Category?) { category = c; viewModelScope.launch { loadMenu() } }
    fun setAvailableOnly(v: Boolean) { availableOnly = v; viewModelScope.launch { loadMenu() } }

    fun refresh() {
        viewModelScope.launch { loadMenu() }
        loadRecommendations()
    }

    private suspend fun loadMenu() {
        val (list, ratings) = withContext(Dispatchers.IO) {
            app.products.search(query, category, availableOnly) to app.products.averageRatings()
        }
        _ratings.value = ratings
        _products.value = list
    }

    /** Gathers all recommendation signals off the main thread; the weather call is network I/O. */
    fun loadRecommendations() {
        val userId = app.session.userId
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val weatherResult = app.weather.current()
                val input = RecommendationEngine.Input(
                    products = app.products.all(),
                    myCounts = app.orders.productCountsFor(userId),
                    baskets = app.orders.basketsAllUsers(),
                    cartProductIds = app.cart.lines(userId).map { it.product.id }.toSet(),
                    weather = weatherResult.weatherOrNull,
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    ratings = app.products.averageRatings()
                )
                weatherResult to RecommendationEngine.recommend(input)
            }
            val (weather, recs) = result
            _weatherLine.value = when (weather) {
                is WeatherResult.Live -> "Based on ${weather.weather.summary()}, your orders and the time of day"
                is WeatherResult.Cached -> "${weather.reason}: ${weather.weather.summary()}"
                is WeatherResult.Unavailable -> "Weather unavailable (${weather.reason}), using your orders and the time of day"
            }
            _recs.value = recs
            app.recStats.recordShown(userId, recs.size)
        }
    }

    /** [fromRecommendation] = added with the + on a recommendation card (counted for the add rate). */
    fun addToCart(product: Product, fromRecommendation: Boolean = false) {
        val userId = app.session.userId
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { app.cart.add(userId, product.id) }
            _message.value = when (res) {
                is CartRepository.CartResult.Ok -> {
                    DataEvents.notifyCartChanged()
                    if (fromRecommendation) app.recStats.recordAdded(userId)
                    loadRecommendations() // basket rules depend on what's in the cart
                    "${product.name} added to cart"
                }
                is CartRepository.CartResult.Error -> res.message
            }
        }
    }

    fun messageShown() { _message.value = null }
}
