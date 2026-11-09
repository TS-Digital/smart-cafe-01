package com.p2897777.ctec3703spec.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * App-wide "something changed" signal. Screens observe it and reload, so an order
 * status updated in the background shows up immediately without manual refresh.
 */
object DataEvents {
    private val _ordersChanged = MutableLiveData(0L)
    val ordersChanged: LiveData<Long> = _ordersChanged

    private val _cartChanged = MutableLiveData(0L)
    val cartChanged: LiveData<Long> = _cartChanged

    fun notifyOrdersChanged() = _ordersChanged.postValue(System.nanoTime())
    fun notifyCartChanged() = _cartChanged.postValue(System.nanoTime())
}
