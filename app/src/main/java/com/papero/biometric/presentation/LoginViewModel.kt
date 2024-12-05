package com.papero.biometric.presentation

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.papero.biometric.R
import com.papero.biometric.domain.entities.LoginResult
import com.papero.biometric.presentation.state.FailedLoginState
import com.papero.biometric.presentation.state.LoginState
import com.papero.biometric.presentation.state.SuccessLoginState
import com.papero.biometric.utilities.SampleAppUser

class LoginViewModel : ViewModel() {

    private val _loginForm = MutableLiveData<LoginState>()
    val loginWithPasswordFormState: LiveData<LoginState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(username: String, password: String) {
        if (isUserNameValid(username) && isPasswordValid(password)) {
            SampleAppUser.username = username
            SampleAppUser.fakeToken = java.util.UUID.randomUUID().toString()
            _loginResult.value = LoginResult(true)
        } else {
            _loginResult.value = LoginResult(false)
        }
    }

    fun onLoginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = FailedLoginState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            _loginForm.value = FailedLoginState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = SuccessLoginState(isDataValid = true)
        }
    }

    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}