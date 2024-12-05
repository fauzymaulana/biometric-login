package com.papero.biometric.presentation.state

sealed class LoginState

data class FailedLoginState(
    val usernameError: Int? = null,
    val passwordError: Int? = null
) : LoginState()

data class SuccessLoginState(
    val isDataValid: Boolean = false
) : LoginState()