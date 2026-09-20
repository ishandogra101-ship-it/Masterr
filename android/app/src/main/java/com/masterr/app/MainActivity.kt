package com.masterr.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MasterrTheme { AppRoot(null) } }
    }
}

class QuickCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        setContent { MasterrTheme { AppRoot(shared ?: "") } }
    }
}

@Composable
fun AppRoot(initialText: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by Prefs.configFlow(ctx).collectAsState(initial = Config())
    var authTick by remember { mutableStateOf(0) }
    var forceSetup by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("chat") }

    val signedIn = remember(cfg, authTick) { if (cfg.firebaseReady) Repo.signedIn(ctx, cfg) else false }

    // Ask for notification permission once we're in.
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(signedIn) {
        if (signedIn) {
            Scheduler.schedule(ctx)
            Prefs.markInteract(ctx)
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(res.data).getResult(Exception::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) { toast(ctx, "No token — check Web client ID & SHA-1"); return@rememberLauncherForActivityResult }
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            scope.launch {
                try { Repo.auth(ctx, cfg).signInWithCredential(cred).await(); authTick++; Scheduler.runNow(ctx) }
                catch (e: Exception) { toast(ctx, "Sign-in failed: ${e.message}") }
            }
        } catch (e: Exception) { toast(ctx, "Google sign-in cancelled/failed") }
    }

    fun startSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(cfg.webClientId).requestEmail().build()
            signInLauncher.launch(GoogleSignIn.getClient(ctx, gso).signInIntent)
        } catch (e: Exception) { toast(ctx, "Could not start sign-in: ${e.message}") }
    }

    fun openDashboard() {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cfg.dashboardUrl))) }
        catch (e: Exception) { toast(ctx, "No browser found") }
    }

    when {
        !cfg.signInReady || forceSetup -> SetupScreen(cfg) { newCfg ->
            scope.launch { Prefs.saveConfig(ctx, newCfg); forceSetup = false; authTick++ }
        }
        !signedIn -> SignInScreen(onSignIn = { startSignIn() }, onReconfigure = { forceSetup = true })
        screen == "settings" -> SettingsScreen(
            onBack = { screen = "chat" },
            onDashboard = { openDashboard() },
            onReconfigure = { forceSetup = true },
            onSignOut = {
                scope.launch {
                    try { Repo.auth(ctx, cfg).signOut() } catch (e: Exception) {}
                    try {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                        GoogleSignIn.getClient(ctx, gso).signOut()
                    } catch (e: Exception) {}
                    authTick++; screen = "chat"
                }
            }
        )
        else -> ChatScreen(cfg = cfg, initialText = initialText,
            onSettings = { screen = "settings" }, onDashboard = { openDashboard() })
    }
}

@Composable
private fun SignInScreen(onSignIn: () -> Unit, onReconfigure: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F2FC)).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Masterr", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7B5CFF))
        Spacer(Modifier.height(8.dp))
        Text("Sign in with the same Google account you use on the web dashboard, so your tasks sync.",
            textAlign = TextAlign.Center, color = Color(0xFF5C5478))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5CFF))) {
            Text("Sign in with Google")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onReconfigure) { Text("Edit config") }
    }
}

private fun toast(ctx: android.content.Context, msg: String) =
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
