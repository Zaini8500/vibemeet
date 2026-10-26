package com.vibemeet.app.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibemeet.app.R
import com.vibemeet.app.data.PublicProfile
import com.vibemeet.app.webrtc.CallState
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
@Composable fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); onDone() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(R.drawable.vibemeet_logo), contentDescription = "VibeMeet logo", modifier = Modifier.size(128.dp)); Text("VibeMeet", style = MaterialTheme.typography.displayMedium); Spacer(Modifier.height(8.dp)); CircularProgressIndicator(); Text("Loading…", modifier = Modifier.padding(top = 12.dp)) } }
}
@Composable fun AuthScreen(onEmailLogin: (String, String) -> Unit, onGoogle: () -> Unit, onCreateAccount: (String, String) -> Unit = { _, _ -> }) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var ageConfirmed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Welcome to VibeMeet", style = MaterialTheme.typography.headlineMedium)
        Text("Meet new people by video. You must be 18 or older to use VibeMeet.", modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ageConfirmed, { ageConfirmed = it }); Text("I confirm I am 18 or older") }
        Button({ onEmailLogin(email, password) }, enabled = email.contains("@") && password.length >= 8 && ageConfirmed, modifier = Modifier.fillMaxWidth()) { Text("Log in with email") }
        OutlinedButton({ onCreateAccount(email, password) }, enabled = email.contains("@") && password.length >= 8 && ageConfirmed, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Create account — then onboarding") }
        OutlinedButton(onGoogle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Continue with Google") }
    }
}
@Composable fun OnboardingScreen(onFinish: (String, String, List<String>, List<String>) -> Unit) {
    // 7 steps, <60s: name, date of birth, gender, photo, country, languages, interests.
    var step by remember { mutableStateOf(0) }; var name by remember { mutableStateOf("") }; var dob by remember { mutableStateOf("") }
    val interests = listOf("Gaming","Football","Cricket","Music","Movies","Technology","Travel","Fitness","Food","Cars","Anime","Education","Business","Photography")
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Step ${step + 1} of 7", style = MaterialTheme.typography.labelLarge)
        when (step) {
            0 -> { Text("What should people call you?", style = MaterialTheme.typography.headlineSmall); OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) }
            1 -> { Text("Your date of birth", style = MaterialTheme.typography.headlineSmall); Text("Used only to check you are 18+. Your full date of birth is never shown to other users."); OutlinedTextField(dob, { dob = it }, label = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) }
            2, 3, 4 -> { Text("Quick setup", style = MaterialTheme.typography.headlineSmall); Text("Gender, profile photo and country are set here in the full app (camera/gallery picker + device-suggested country, editable).") }
            5 -> { Text("Languages", style = MaterialTheme.typography.headlineSmall); Text("English, Urdu, Hindi, Arabic, Turkish, Spanish, French — pick all you speak.") }
            else -> { Text("Pick your interests", style = MaterialTheme.typography.headlineSmall); LazyColumn { items(interests) { i -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(selected.contains(i), { if (selected.contains(i)) selected.remove(i) else selected.add(i) }); Text(i) } } } }
        }
        Spacer(Modifier.height(16.dp))
        Button({ if (step < 6) step++ else onFinish(name, dob, selected.toList(), listOf("en")) }, modifier = Modifier.fillMaxWidth()) { Text(if (step < 6) "Next" else "Finish") }
    }
}
@Composable fun HomeScreen(profile: PublicProfile?, onStart: () -> Unit, onProfile: () -> Unit, onConnections: () -> Unit, onReportSafety: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(profile?.displayName ?: "Hi there", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.weight(1f)); TextButton(onProfile) { Text("Profile") } }
        Text("${profile?.age ?: ""} · ${profile?.country ?: ""}")
        Text(profile?.interests?.joinToString(", ") ?: "")
        Spacer(Modifier.height(32.dp))
        Button(onStart, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("START MATCHING", style = MaterialTheme.typography.titleLarge) }
        OutlinedButton(onConnections, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Connections") }
        TextButton(onReportSafety, modifier = Modifier.padding(top = 12.dp)) { Text("Safety & reporting") }
        Text("Country, language, interest and gender preferences (where permitted) are set before matching. Premium filters are optional — safety features are never paid.", modifier = Modifier.padding(top = 16.dp))
    }
}
@Composable fun MatchingScreen(onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Finding someone for you…", modifier = Modifier.padding(16.dp)); OutlinedButton(onCancel) { Text("Cancel") } } }
}
@Composable fun VideoChatScreen(callState: CallState, icebreakers: List<String>, onNext: () -> Unit, onEnd: () -> Unit, onMute: () -> Unit, onCamera: () -> Unit, onSwitch: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit, onFavorite: () -> Unit, eglContext: EglBase.Context? = null, localTrack: VideoTrack? = null, remoteTrack: VideoTrack? = null) {
    // Layout: remote video full-screen, local preview floating, controls bottom.
    Box(Modifier.fillMaxSize()) {
        if (eglContext != null && remoteTrack != null) {
            AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).apply { init(eglContext, null); setMirror(true) } }, update = { renderer -> remoteTrack.addSink(renderer) }, modifier = Modifier.fillMaxSize())
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text(when (callState) { CallState.CONNECTING -> "Connecting…"; CallState.CONNECTED -> "Connected"; CallState.POOR -> "Poor connection"; CallState.RECONNECTING -> "Reconnecting…"; CallState.DISCONNECTED -> "Disconnected"; CallState.IDLE -> "Waiting…" }) } }
        }
        if (eglContext != null && localTrack != null) {
            AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).apply { init(eglContext, null); setMirror(true) } }, update = { renderer -> localTrack.addSink(renderer) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(110.dp, 150.dp))
        }
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) { Text("VibeMeet call", style = MaterialTheme.typography.labelLarge); icebreakers.firstOrNull()?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) } }
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onMute) { Text("Mute") }; Button(onCamera) { Text("Camera") }; Button(onSwitch) { Text("Flip") }
            Button(onNext) { Text("NEXT") }; OutlinedButton(onEnd) { Text("End") }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp)) { TextButton(onReport) { Text("Report") }; TextButton(onBlock) { Text("Block") }; TextButton(onFavorite) { Text("♥ Favorite") } }
    }
}
@Composable fun ProfileScreen(profile: PublicProfile?, onDelete: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(profile?.displayName ?: "Profile", style = MaterialTheme.typography.headlineMedium)
        Text("${profile?.age ?: ""} · ${profile?.country ?: ""} · ${profile?.verificationStatus ?: ""}")
        Text(profile?.bio ?: ""); Text(profile?.interests?.joinToString(", ") ?: "")
        Text("Email, phone, exact location, IP and device info are never shown here or to other users.", modifier = Modifier.padding(vertical = 12.dp))
        OutlinedButton(onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        TextButton(onDelete, modifier = Modifier.padding(top = 8.dp)) { Text("Delete my account") }
    }
}
@Composable fun ConnectionsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Previous connections", style = MaterialTheme.typography.headlineMedium); Text("Your recent matches appear here from GET /session/history. Favorites and connection requests stay inside VibeMeet — no contact details are shared.", modifier = Modifier.padding(vertical = 12.dp)); OutlinedButton(onBack) { Text("Back") } }
}
