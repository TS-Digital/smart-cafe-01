package com.p2897777.ctec3703spec.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.p2897777.ctec3703spec.app
import com.p2897777.ctec3703spec.data.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared by login and register screens. Password hashing is slow by design, so it runs off the main thread. */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    sealed class State {
        object Idle : State()
        object Loading : State()
        object LoggedIn : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    private val app = application.app

    fun login(email: String, password: String) = execute { app.users.login(email, password) }

    fun register(name: String, email: String, password: String) = execute { app.users.register(name, email, password) }

    private fun execute(block: () -> AuthResult) {
        if (_state.value == State.Loading) return
        _state.value = State.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching(block).getOrElse { AuthResult.Failure("Something went wrong. Please try again.") }
            }
            _state.value = when (result) {
                is AuthResult.Success -> {
                    app.session.login(result.user.id, result.user.name)
                    State.LoggedIn
                }
                is AuthResult.Failure -> State.Error(result.message)
            }
        }
    }
}
