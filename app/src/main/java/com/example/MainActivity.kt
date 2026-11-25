package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.ShopRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ThennavalShopShell
import com.example.ui.theme.ThennavalComboTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ShopViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ThennavalComboTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.initialize(context)
    }

    val currentAuth = authState
    if (currentAuth !is AuthUiState.Authenticated) {
        AuthScreen(
            authState = currentAuth,
            onSignInWithCredentials = { username, password ->
                authViewModel.signInWithUsernameAndPassword(context, username, password)
            },
            onGoogleSignIn = { authViewModel.signInWithGoogle(context) },
            onRefreshApproval = { authViewModel.refreshApprovalStatus(context) },
            onSignOut = { authViewModel.signOut(context) }
        )
    } else {
        val uid = currentAuth.profile.userId.ifBlank { currentAuth.profile.id }
        key(uid) {
            val shopViewModel: ShopViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        ShopViewModel(
                            repository = ShopRepository(context.applicationContext),
                            currentUserId = uid
                        )
                    }
                }
            )
            ThennavalShopShell(
                viewModel = shopViewModel,
                onRefreshApproval = { authViewModel.refreshApprovalStatus(context) },
                onSignOut = { authViewModel.signOut(context) }
            )
        }
    }
}
