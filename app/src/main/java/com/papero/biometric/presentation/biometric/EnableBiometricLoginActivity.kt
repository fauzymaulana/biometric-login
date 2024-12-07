package com.papero.biometric.presentation.biometric

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.papero.biometric.R
import com.papero.biometric.domain.usecase.cryptograph.CryptographyManager
import com.papero.biometric.domain.usecase.cryptograph.CryptographyManagerImpl
import com.papero.biometric.presentation.LoginViewModel
import com.papero.biometric.presentation.state.FailedLoginState
import com.papero.biometric.presentation.state.SuccessLoginState
import com.papero.biometric.utilities.BiometricPromptUtils
import com.papero.biometric.utilities.CIPHERTEXT_WRAPPER
import com.papero.biometric.utilities.SHARED_PREFS_FILENAME
import com.papero.biometric.utilities.SampleAppUser

class EnableBiometricLoginActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "EnableBiometricLogin"
    private lateinit var cryptographyManager: CryptographyManager
    private val viewModel by viewModels<LoginViewModel>()

    private var btnCancel: Button? = null
    private var btnAuthorize: Button? = null
    private var etUsername: EditText? = null
    private var etPassword: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_enable_biometric_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initViews()
        initListeners()
        watcherUsername()
        watcherPassword()
        setupForLoginWithPassword()

    }

    private fun initViews() {
        btnCancel = findViewById(R.id.btn_cancel)
        btnAuthorize = findViewById(R.id.btn_authorize)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
    }

    private fun initListeners() {
        btnCancel?.setOnClickListener(this)
        btnAuthorize?.setOnClickListener(this)
    }

    private fun watcherUsername() {
        etUsername?.doAfterTextChanged {
            viewModel.onLoginDataChanged(
                username = etUsername?.text.toString(),
                password = etPassword?.text.toString()
            )
        }
    }

    private fun watcherPassword() {
        etPassword?.doAfterTextChanged {
            viewModel.onLoginDataChanged(
                username = etUsername?.text.toString(),
                password = etPassword?.text.toString()
            )
        }

        etPassword?.setOnEditorActionListener { _, actionId, _ ->
            when(actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    viewModel.login(
                        username = etUsername?.text.toString(),
                        password = etPassword?.text.toString()
                    )
                }
            }

            false
        }
    }

    private fun setupForLoginWithPassword() {
        viewModel.loginWithPasswordFormState.observe(this) { formState ->
            val loginState = formState ?: return@observe

            when(loginState) {
                is SuccessLoginState -> {
                    btnAuthorize?.isEnabled = loginState.isDataValid
                }

                is FailedLoginState -> {
                    loginState.usernameError?.let { etUsername?.error = getString(it) }
                    loginState.passwordError?.let { etPassword?.error = getString(it) }
                }
            }
        }

        viewModel.loginResult.observe(this) {
            val loginResult = it ?: return@observe

            if (loginResult.success) {
                showBiometricPromptForEncryption()
            }
        }
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            btnCancel?.id -> {
                finish()
            }

            btnAuthorize?.id -> {
                viewModel.login(
                    username = etUsername?.text.toString(),
                    password = etPassword?.text.toString()
                )
            }
        }
    }

    private fun showBiometricPromptForEncryption() {
        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val secretKeyName = "biometric_sample_encryption_key"
            cryptographyManager = CryptographyManagerImpl()
            val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName)
            val biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(this, ::encryptAndStoreServerToken)
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun encryptAndStoreServerToken(authResult: BiometricPrompt.AuthenticationResult) {
        authResult.cryptoObject?.cipher?.apply {
            SampleAppUser.fakeToken?.let { token ->
                Log.d(TAG, "The token from server is $token")
                val encryptedServerTokenWrapper = cryptographyManager.encryptData(token, this)
                cryptographyManager.persistCiphertextWrapperToSharedPrefs(
                    encryptedServerTokenWrapper,
                    applicationContext,
                    SHARED_PREFS_FILENAME,
                    Context.MODE_PRIVATE,
                    CIPHERTEXT_WRAPPER
                )
            }
        }
        finish()
    }
}