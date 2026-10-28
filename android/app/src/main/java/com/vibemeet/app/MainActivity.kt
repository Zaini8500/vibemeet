package com.vibemeet.app
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.vibemeet.app.data.TokenStore
import com.vibemeet.app.ui.screens.*
import com.vibemeet.app.ui.theme.VibeMeetTheme
import com.vibemeet.app.ui.viewmodel.*
import com.vibemeet.app.webrtc.CallState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
@AndroidEntryPoint class MainActivity : ComponentActivity() {
    @Inject lateinit var tokens: TokenStore
    private var pendingMatching by mutableStateOf(false)
    // Google idToken delivered by the launcher; the composable layer then
    // hands it to AuthViewModel.google() (existing user -> home, new user ->
    // onboarding, then /auth/google/complete). The token is never stored.
    private var googleIdTokenResult by mutableStateOf<String?>(null)
    // Created lazily on first Google tap only, never at launch: creating the
    // Google sign-in client in onCreate is unnecessary work before the user
    // ever taps Google, and can fail on devices with outdated Play Services.
    private val googleClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID).requestEmail().build()
        GoogleSignIn.getClient(this, gso)
    }
    // Set when Google sign-in itself fails (no idToken returned). It is
    // forwarded into AuthViewModel inside setContent, so the user sees an
    // error instead of a silent no-op. The Android OAuth client does not
    // exist yet, so Google may still fail — the fix is visible feedback.
    private var pendingGoogleError by mutableStateOf<String?>(null)
    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token != null) googleIdTokenResult = token else pendingGoogleError = "Google sign-in failed — try email instead"
        } catch (_: Exception) { pendingGoogleError = "Google sign-in failed — try email instead" }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.CAMERA] == true && granted[Manifest.permission.RECORD_AUDIO] == true) pendingMatching = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VibeMeetTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                val authVm: AuthViewModel = hiltViewModel()
                val homeVm: HomeViewModel = hiltViewModel()
                val matchVm: MatchingViewModel = hiltViewModel()
                val videoVm: VideoViewModel = hiltViewModel()
                val profile by homeVm.profile.collectAsState()
                val callState by videoVm.callState.collectAsState()
                val remoteTrack by videoVm.webrtc.remoteTrack.collectAsState()
                val localTrack by videoVm.webrtc.localTrackFlow.collectAsState()
                var micOn by remember { mutableStateOf(true) }
                var camOn by remember { mutableStateOf(true) }
                var pendingEmail by remember { mutableStateOf("") }
                var pendingPassword by remember { mutableStateOf("") }
                var pendingMatch by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
                // Signalling is connected when Home is entered (after login or
                // after the splash token check). match_found navigates to video
                // and starts the real WebRTC call (initiator creates the offer).
                LaunchedEffect(pendingMatch) {
                    pendingMatch?.let { (sid, pid, initiator) -> videoVm.startCall(sid, pid, initiator); pendingMatch = null }
                }
                LaunchedEffect(pendingMatching) {
                    if (pendingMatching) { pendingMatching = false; nav.navigate("matching") }
                }
                LaunchedEffect(pendingGoogleError) {
                    pendingGoogleError?.let { authVm.setError(it); pendingGoogleError = null }
                }
                LaunchedEffect(googleIdTokenResult) {
                    googleIdTokenResult?.let { idToken ->
                        authVm.google(idToken, onAuthed = { homeVm.load(); nav.navigate("home") }, onNeedsOnboarding = { nav.navigate("onboarding") })
                        googleIdTokenResult = null
                    }
                }
                NavHost(nav, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen {
                            scope.launch {
                                // A token-read failure must never crash the
                                // app: fall back to the login screen.
                                val dest = try {
                                    if (tokens.accessToken.first() != null) "home" else "auth"
                                } catch (_: Exception) { "auth" }
                                try {
                                    nav.navigate(dest) { popUpTo("splash") { inclusive = true } }
                                } catch (_: Exception) { nav.navigate("auth") }
                            }
                        }
                    }
                    composable("auth") {
                        val err by authVm.error.collectAsState()
                        val loading by authVm.loading.collectAsState()
                        AuthScreen(
                            { email, pass -> authVm.login(email, pass) { homeVm.load(); nav.navigate("home") } },
                            { try { googleLauncher.launch(googleClient.signInIntent) } catch (_: Exception) { authVm.setError("Google sign-in failed — try email instead") } },
                            { email, pass -> pendingEmail = email; pendingPassword = pass; nav.navigate("onboarding") },
                            error = err,
                            loading = loading
                        )
                    }
                    composable("onboarding") {
                        val needsGoogle by authVm.needsGoogleOnboarding.collectAsState()
                        val onbErr by authVm.error.collectAsState()
                        val onbLoading by authVm.loading.collectAsState()
                        OnboardingScreen(
                            onFinish = { name, dob, gender, interests, langs ->
                                // Username: lowercase, letters/digits only. The
                                // onboarding step already requires 3+ letters;
                                // this is only a safety fallback, never sent invalid.
                                val base = name.lowercase().filter { it.isLetterOrDigit() }
                                val username = if (base.length >= 3) base else base + "user"
                                // Backend gender enum: female/male/non_binary/prefer_not_to_say.
                                val genderEnum = when (gender) {
                                    "Female" -> "female"; "Male" -> "male"; "Non-binary" -> "non_binary"
                                    else -> "prefer_not_to_say"
                                }
                                val body = mapOf("username" to username, "displayName" to name, "dateOfBirth" to dob, "country" to "PK", "gender" to genderEnum)
                                val profileBody = mapOf("interests" to interests, "languages" to langs, "gender" to genderEnum)
                                if (needsGoogle) {
                                    authVm.googleComplete(body) { homeVm.saveProfile(profileBody) { homeVm.load(); nav.navigate("home") } }
                                } else {
                                    // Credentials collected on the Auth screen (not hardcoded),
                                    // combined here with the onboarding answers for registration.
                                    authVm.register(body + mapOf("email" to pendingEmail, "password" to pendingPassword)) {
                                        homeVm.saveProfile(profileBody) { homeVm.load(); nav.navigate("home") }
                                    }
                                }
                            },
                            error = onbErr,
                            loading = onbLoading
                        )
                    }
                    composable("home") {
                        val homeError by homeVm.error.collectAsState()
                        LaunchedEffect(Unit) {
                            videoVm.connectSignaling { sid, pid, initiator -> pendingMatch = Triple(sid, pid, initiator); nav.navigate("video") }
                            homeVm.load()
                        }
                        HomeScreen(profile, { requestCallPermissions { nav.navigate("matching") } }, { nav.navigate("profile") }, { nav.navigate("connections") }, { nav.navigate("profile") }, error = homeError, onRetry = { homeVm.load() })
                    }
                    composable("matching") {
                        MatchingScreen { matchVm.cancel(); nav.popBackStack() }
                        LaunchedEffect(Unit) { matchVm.start { /* signalling will emit match_found */ } }
                    }
                    composable("video") {
                        VideoChatScreen(
                            callState, videoVm.icebreakers(profile?.interests ?: emptyList()),
                            onNext = { videoVm.next { nav.navigate("matching") } },
                            onEnd = { videoVm.endCall(); nav.navigate("home") },
                            onMute = { micOn = !micOn; videoVm.toggleMic(micOn) },
                            onCamera = { camOn = !camOn; videoVm.toggleCamera(camOn) },
                            onSwitch = { videoVm.switchCamera() },
                            onReport = { videoVm.report("other") { nav.navigate("home") } },
                            onBlock = { videoVm.block { nav.navigate("home") } },
                            onFavorite = { videoVm.favorite() },
                            eglContext = videoVm.webrtc.eglContext,
                            localTrack = localTrack,
                            remoteTrack = remoteTrack
                        )
                    }
                    composable("profile") { ProfileScreen(profile, { scope.launch { /* confirm in UI, then delete */ } }, { authVm.logout(); nav.navigate("auth") }) }
                    composable("connections") { ConnectionsScreen { nav.popBackStack() } }
                }
            }
        }
    }
    private fun requestCallPermissions(onGranted: () -> Unit) {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) onGranted() else permissionLauncher.launch(needed.toTypedArray())
    }
    override fun onPause() {
        super.onPause()
        // Production wiring: pause the local video track here so camera/mic
        // are released when backgrounded, and resume on return.
    }
}
