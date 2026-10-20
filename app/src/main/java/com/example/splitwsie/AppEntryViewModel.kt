package com.example.splitwsie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    private val _nextDestination = MutableStateFlow<String?>(null)
    val nextDestination: StateFlow<String?> = _nextDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = try {
                val userId = sessionManager.currentUserId
                if (userId != null && userRepository.getUserById(userId) != null) {
                    Screen.Home.route
                } else {
                    if (userId != null) sessionManager.clearSession()
                    Screen.Auth.route
                }
            } catch (e: Exception) {
                Screen.Auth.route
            }
            _nextDestination.value = destination
            _startDestination.value = Screen.Welcome.route
        }
    }
}
