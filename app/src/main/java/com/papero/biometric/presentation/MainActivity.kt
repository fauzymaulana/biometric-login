package com.papero.biometric.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.papero.biometric.R
import com.papero.biometric.domain.usecase.cryptograph.CryptographyManager
import com.papero.biometric.domain.usecase.cryptograph.CryptographyManagerImpl
import com.papero.biometric.presentation.biometric.EnableBiometricLoginActivity
import com.papero.biometric.presentation.state.FailedLoginState
import com.papero.biometric.presentation.state.SuccessLoginState
import com.papero.biometric.utilities.BiometricPromptUtils
import com.papero.biometric.utilities.CIPHERTEXT_WRAPPER
import com.papero.biometric.utilities.SHARED_PREFS_FILENAME
import com.papero.biometric.utilities.SampleAppUser

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var etUsername: EditText? = null
    private var etPassword: EditText? = null
    private var btnLogin: Button? = null
    private var btnUseBiometric: TextView? = null
    private var txtProgress: TextView? = null

    private val viewModel by viewModels<LoginViewModel>()

    private lateinit var biometricPrompt: BiometricPrompt
    private val cryptographyManager = CryptographyManagerImpl()
    private val ciphertextWrapper
        get() = cryptographyManager.getCiphertextWrapperFromSharedPrefs(
                applicationContext,
                SHARED_PREFS_FILENAME,
                Context.MODE_PRIVATE,
                CIPHERTEXT_WRAPPER
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
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

        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            btnUseBiometric?.visibility = View.VISIBLE
        } else {
            btnUseBiometric?.visibility = View.INVISIBLE
        }

        if (ciphertextWrapper == null) {
            setupForLoginWithPassword()
        }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnUseBiometric = findViewById(R.id.btn_use_biometric)
        txtProgress = findViewById(R.id.txt_success)
    }

    private fun initListeners() {
        btnLogin?.setOnClickListener(this)
        btnUseBiometric?.setOnClickListener(this)
    }

    private fun setupForLoginWithPassword() {
        viewModel.loginWithPasswordFormState.observe(this) { formState ->
            val loginState = formState ?: return@observe

            when(loginState) {
                is SuccessLoginState -> {
                    btnLogin?.isEnabled = loginState.isDataValid
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
                updateApp(
                    "You successfully signed up using password as: user " +
                            "${SampleAppUser.username} with fake token ${SampleAppUser.fakeToken}"
                )
            }
        }
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

    override fun onClick(v: View?) {
        when(v?.id) {
            btnLogin?.id -> {
                viewModel.login(
                    username = etUsername?.text.toString(),
                    password = etPassword?.text.toString()
                )
            }

            btnUseBiometric?.id -> {
                if (ciphertextWrapper != null) {
                    showBiometricPromptForDecryption()
                } else {
                    startActivity(Intent(this, EnableBiometricLoginActivity::class.java))
                }
            }
        }
    }

    private fun updateApp(successMsg: String) {
        txtProgress?.text = successMsg
    }

    /**
     * The logic is kept inside onResume instead of onCreate so that authorizing biometrics takes
     * immediate effect.
     */
    override fun onResume() {
        super.onResume()

        if (ciphertextWrapper != null) {
            if (SampleAppUser.fakeToken == null) {
                showBiometricPromptForDecryption()
            } else {
                updateApp(getString(R.string.already_signedin))
            }
        }
    }

    private fun showBiometricPromptForDecryption() {
        ciphertextWrapper?.let { textWrapper ->
            val secretKeyName = getString(R.string.secret_key_name)
            val cipher = cryptographyManager.getInitializedCipherForDecryption(
                secretKeyName, textWrapper.initializationVector
            )
            biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(
                    this,
                    ::decryptServerTokenFromStorage
                )
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        ciphertextWrapper?.let { textWrapper ->
            authResult.cryptoObject?.cipher?.let {
                val plaintext =
                    cryptographyManager.decryptData(textWrapper.ciphertext, it)
                SampleAppUser.fakeToken = plaintext
                // Now that you have the token, you can query server for everything else
                // the only reason we call this fakeToken is because we didn't really get it from
                // the server. In your case, you will have gotten it from the server the first time
                // and therefore, it's a real token.

                updateApp(getString(R.string.already_signedin))
            }
        }
    }
}