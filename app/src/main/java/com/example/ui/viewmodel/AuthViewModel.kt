package com.example.ui.viewmodel

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthManager
import com.example.data.firebase.StaffAuthRepository
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class Unauthenticated(val googleEmail: String? = null) : AuthUiState
    data object Authenticating : AuthUiState
    data class Authenticated(val profile: UserProfile) : AuthUiState
    data class PendingApproval(val profile: UserProfile) : AuthUiState
    data class Error(
        val message: String,
        val googleEmail: String? = null
    ) : AuthUiState
}

sealed interface RoleAccessOutcome {
    data class Verified(val profile: UserProfile) : RoleAccessOutcome
    data class PendingApproval(val profile: UserProfile) : RoleAccessOutcome
}

class AuthViewModel(
    private val staffAuthRepositoryProvider: (Context) -> StaffAuthRepository = { ctx ->
        StaffAuthRepository(ctx.applicationContext)
    }
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Unauthenticated(googleEmail = null))
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        viewModelScope.launch {
            delay(150)
            val currentUser = withContext(Dispatchers.IO) {
                val firebaseAuth = Firebase.auth
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    if (auth.currentUser == null && _authState.value !is AuthUiState.Authenticating) {
                        _authState.value = AuthUiState.Unauthenticated(googleEmail = null)
                    }
                }
                authStateListener = listener
                firebaseAuth.addAuthStateListener(listener)
                firebaseAuth.currentUser
            }
            if (_authState.value is AuthUiState.Unauthenticated && currentUser != null) {
                _authState.value = AuthUiState.Unauthenticated(googleEmail = currentUser.email)
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        if (_authState.value is AuthUiState.Authenticating) return
        viewModelScope.launch {
            _authState.value = AuthUiState.Authenticating
            val result = AuthManager.signInWithGoogle(
                context = context,
                onCancelled = {
                    _authState.value = AuthUiState.Unauthenticated(
                        googleEmail = Firebase.auth.currentUser?.email
                    )
                }
            )
            result.onSuccess { firebaseUser ->
                val ensured = staffAuthRepositoryProvider(context.applicationContext)
                    .ensureUserProfile(firebaseUser)
                ensured.onSuccess {
                    _authState.value = AuthUiState.Unauthenticated(googleEmail = firebaseUser.email)
                }.onFailure { err ->
                    _authState.value = AuthUiState.Error(
                        message = err.localizedMessage ?: "Unable to connect staff profile in Firestore.",
                        googleEmail = firebaseUser.email
                    )
                }
            }.onFailure { err ->
                if (err is GetCredentialCancellationException) {
                    _authState.value = AuthUiState.Unauthenticated(
                        googleEmail = Firebase.auth.currentUser?.email
                    )
                } else {
                    _authState.value = AuthUiState.Error(
                        message = err.localizedMessage ?: "Google Sign-In failed. Please try again.",
                        googleEmail = Firebase.auth.currentUser?.email
                    )
                }
            }
        }
    }

    fun signInWithUsernameAndPassword(
        context: Context,
        username: String,
        password: String
    ) {
        if (_authState.value is AuthUiState.Authenticating) return
        val cleanUser = username.trim()
        if (cleanUser.length < 2) {
            _authState.value = AuthUiState.Error(
                message = "Enter a valid username (at least 2 characters).",
                googleEmail = Firebase.auth.currentUser?.email
            )
            return
        }
        if (password.length < 4) {
            _authState.value = AuthUiState.Error(
                message = "Enter a password with at least 4 characters.",
                googleEmail = Firebase.auth.currentUser?.email
            )
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Authenticating
            val appContext = context.applicationContext
            val firebaseUser = Firebase.auth.currentUser ?: run {
                val googleRes = AuthManager.signInWithGoogle(
                    context = context,
                    onCancelled = {
                        _authState.value = AuthUiState.Unauthenticated(googleEmail = null)
                    }
                )
                googleRes.getOrElse { err ->
                    if (err is GetCredentialCancellationException) {
                        _authState.value = AuthUiState.Unauthenticated(googleEmail = null)
                    } else {
                        _authState.value = AuthUiState.Error(
                            message = err.localizedMessage ?: "Google Sign-In is required to verify against Firestore.",
                            googleEmail = null
                        )
                    }
                    return@launch
                }
            }

            val validation = staffAuthRepositoryProvider(appContext).validateStaffCredentials(
                firebaseUser = firebaseUser,
                rawUsername = cleanUser,
                rawPassword = password
            )
            validation.onSuccess { profile ->
                handleProfileAccess(profile)
            }.onFailure { err ->
                _authState.value = AuthUiState.Error(
                    message = err.localizedMessage ?: "Invalid username or password.",
                    googleEmail = firebaseUser.email
                )
            }
        }
    }

    fun refreshApprovalStatus(context: Context) {
        val currentUser = Firebase.auth.currentUser ?: run {
            _authState.value = AuthUiState.Unauthenticated(googleEmail = null)
            return
        }
        viewModelScope.launch {
            _authState.value = AuthUiState.Authenticating
            verifyUserProfile(context.applicationContext, currentUser)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            AuthManager.signOut(context.applicationContext)
            _authState.value = AuthUiState.Unauthenticated(googleEmail = null)
        }
    }

    private suspend fun verifyUserProfile(
        context: Context,
        firebaseUser: FirebaseUser
    ) {
        val res = staffAuthRepositoryProvider(context).ensureUserProfile(firebaseUser)
        res.onSuccess { profile ->
            handleProfileAccess(profile)
        }.onFailure { err ->
            _authState.value = AuthUiState.Error(
                message = err.localizedMessage ?: "Unable to verify staff profile in Firestore.",
                googleEmail = firebaseUser.email
            )
        }
    }

    private fun handleProfileAccess(profile: UserProfile) {
        when (val outcome = evaluateProfileAccess(profile)) {
            is RoleAccessOutcome.Verified -> {
                _authState.value = AuthUiState.Authenticated(outcome.profile)
            }
            is RoleAccessOutcome.PendingApproval -> {
                _authState.value = AuthUiState.PendingApproval(outcome.profile)
            }
        }
    }

    override fun onCleared() {
        authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
        authStateListener = null
        super.onCleared()
    }

    companion object {
        fun evaluateProfileAccess(profile: UserProfile): RoleAccessOutcome {
            return if (!profile.active) {
                RoleAccessOutcome.PendingApproval(profile)
            } else {
                RoleAccessOutcome.Verified(profile)
            }
        }
    }
}
