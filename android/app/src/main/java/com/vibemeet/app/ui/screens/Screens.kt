package com.vibemeet.app.ui.screens
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibemeet.app.R
import com.vibemeet.app.data.PublicProfile
import com.vibemeet.app.webrtc.CallState
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.time.LocalDate
import java.time.Period

private val VibeViolet = Color(0xFF6C4CF1)
private val VibePink = Color(0xFFFF6B9D)
private val VibeTeal = Color(0xFF00C2A8)
private val VibeGradient = Brush.verticalGradient(listOf(VibeViolet, VibePink))

@Composable fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); onDone() }
    Box(Modifier.fillMaxSize().background(VibeGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.vibemeet_logo), contentDescription = "VibeMeet logo", modifier = Modifier.size(128.dp))
            Text("VibeMeet", style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = Color.White)
            Text("Loading…", modifier = Modifier.padding(top = 12.dp), color = Color.White)
        }
    }
}

@Composable fun AuthScreen(
    onEmailLogin: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onCreateAccount: (String, String) -> Unit = { _, _ -> },
    error: String? = null,
    loading: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ageConfirmed by remember { mutableStateOf(false) }
    val formValid = email.contains("@") && password.length >= 8 && ageConfirmed
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(VibeGradient), contentAlignment = Alignment.Center) {
            Text("VibeMeet", style = MaterialTheme.typography.displayMedium, color = Color.White)
        }
        Column(Modifier.padding(24.dp)) {
            Text("Welcome to VibeMeet", style = MaterialTheme.typography.headlineMedium)
            Text("Meet new people by video. You must be 18 or older to use VibeMeet.", modifier = Modifier.padding(vertical = 12.dp))
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (loading) {
                Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Please wait…", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !loading)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !loading)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(ageConfirmed, { ageConfirmed = it }, enabled = !loading)
                        Text("I confirm I am 18 or older")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button({ onEmailLogin(email, password) }, enabled = formValid && !loading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Log in with email") }
            OutlinedButton({ onCreateAccount(email, password) }, enabled = formValid && !loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(24.dp)) { Text("Create account — then onboarding") }
            OutlinedButton(onGoogle, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(24.dp)) { Text("Continue with Google") }
            Text("Create account becomes active once you enter an email with @, a password of 8 or more characters, and tick the 18+ box.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private fun parseDob(dob: String): LocalDate? {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dob)) return null
    return try { LocalDate.parse(dob) } catch (_: Exception) { null }
}
private fun ageInYears(dob: LocalDate): Int = try { Period.between(dob, LocalDate.now()).years } catch (_: Exception) { -1 }

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OnboardingScreen(
    onFinish: (name: String, dob: String, gender: String, interests: List<String>, languages: List<String>) -> Unit,
    error: String? = null,
    loading: Boolean = false
) {
    // 7 steps, <60s: name, date of birth, gender, country, photo (optional), languages, interests.
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    val languages = listOf("en" to "English", "ur" to "Urdu", "hi" to "Hindi", "ar" to "Arabic", "tr" to "Turkish", "es" to "Spanish", "fr" to "French")
    val selectedLangs = remember { mutableStateListOf<String>() }
    val interests = listOf("Gaming", "Football", "Cricket", "Music", "Movies", "Technology", "Travel", "Fitness", "Food", "Cars", "Anime", "Education", "Business", "Photography")
    val selected = remember { mutableStateListOf<String>() }
    val nameLetters = name.filter { it.isLetter() }.length
    val dobParsed = parseDob(dob)
    val dobAge = if (dobParsed != null) ageInYears(dobParsed) else -1
    val dobValid = dobParsed != null && dobAge >= 18
    val stepValid = when (step) {
        0 -> nameLetters >= 3
        1 -> dobValid
        2 -> gender.isNotBlank()
        3 -> true // country is fixed to PK, shown below; nothing to choose
        4 -> true // profile photo is optional; Next skips it
        5 -> selectedLangs.isNotEmpty()
        else -> selected.isNotEmpty()
    }
    val stepError: String? = when (step) {
        0 -> if (name.isNotEmpty() && nameLetters < 3) "Name must have at least 3 letters." else null
        1 -> when {
            dob.isEmpty() -> null
            dobParsed == null -> "Date of birth must be in YYYY-MM-DD format, for example 2000-05-12."
            dobAge < 18 -> "You must be 18 or older to use VibeMeet."
            else -> null
        }
        2 -> if (gender.isBlank()) "Please choose a gender option." else null
        5 -> if (selectedLangs.isEmpty()) "Please choose at least one language." else null
        6 -> if (selected.isEmpty()) "Please choose at least one interest." else null
        else -> null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        Text("Step ${step + 1} of 7", style = MaterialTheme.typography.labelLarge, color = VibeViolet)
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (loading) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Please wait…", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        when (step) {
            0 -> {
                Text("What should people call you?", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), singleLine = true, enabled = !loading)
                Text("At least 3 letters.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            1 -> {
                Text("Your date of birth", style = MaterialTheme.typography.headlineSmall)
                Text("Used only to check you are 18+. Your full date of birth is never shown to other users.")
                OutlinedTextField(dob, { dob = it }, label = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), singleLine = true, enabled = !loading)
            }
            2 -> {
                Text("Gender", style = MaterialTheme.typography.headlineSmall)
                Text("Choose one.", modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Female", "Male", "Non-binary", "Prefer not to say").forEach { g ->
                        FilterChip(gender == g, { gender = g }, { Text(g) }, enabled = !loading)
                    }
                }
            }
            3 -> {
                Text("Country", style = MaterialTheme.typography.headlineSmall)
                Text("Country is set to PK for this version.", modifier = Modifier.padding(vertical = 8.dp))
                Card(shape = RoundedCornerShape(12.dp)) { Text("Pakistan (PK)", modifier = Modifier.padding(16.dp)) }
            }
            4 -> {
                Text("Profile photo", style = MaterialTheme.typography.headlineSmall)
                Text("Profile photo is optional for now and can be added later. Tap Next to continue.", modifier = Modifier.padding(vertical = 8.dp))
            }
            5 -> {
                Text("Languages", style = MaterialTheme.typography.headlineSmall)
                Text("Pick all you speak.", modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { (code, label) ->
                        FilterChip(selectedLangs.contains(code), { if (selectedLangs.contains(code)) selectedLangs.remove(code) else selectedLangs.add(code) }, { Text(label) }, enabled = !loading)
                    }
                }
            }
            else -> {
                Text("Pick your interests", style = MaterialTheme.typography.headlineSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    interests.forEach { i ->
                        FilterChip(selected.contains(i), { if (selected.contains(i)) selected.remove(i) else selected.add(i) }, { Text(i) }, enabled = !loading)
                    }
                }
            }
        }
        if (stepError != null) {
            Text(stepError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) {
                OutlinedButton({ step-- }, enabled = !loading, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) { Text("Back") }
            }
            Button(
                {
                    if (step < 6) step++
                    else onFinish(name.trim(), dob, gender, selected.toList(), selectedLangs.toList())
                },
                enabled = stepValid && !loading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            ) { Text(if (step < 6) "Next" else "Finish") }
        }
    }
}

@Composable fun HomeScreen(
    profile: PublicProfile?,
    onStart: () -> Unit,
    onProfile: () -> Unit,
    onConnections: () -> Unit,
    onReportSafety: () -> Unit,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(VibeGradient), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(profile?.displayName ?: "Hi there", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("${profile?.age ?: ""} · ${profile?.country ?: ""}", color = Color.White)
            }
        }
        Column(Modifier.padding(24.dp)) {
            if (profile == null && error != null) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Profile could not be loaded — Retry", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onRetry, shape = RoundedCornerShape(24.dp)) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onProfile) { Text("Profile") }
            }
            Text(profile?.interests?.joinToString(", ") ?: "")
            Spacer(Modifier.height(24.dp))
            Button(onStart, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp)) { Text("START MATCHING", style = MaterialTheme.typography.titleLarge) }
            OutlinedButton(onConnections, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(24.dp)) { Text("Connections") }
            TextButton(onReportSafety, modifier = Modifier.padding(top = 12.dp)) { Text("Safety & reporting") }
            Text("Country, language, interest and gender preferences (where permitted) are set before matching. Premium filters are optional — safety features are never paid.", modifier = Modifier.padding(top = 16.dp))
        }
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
            BoxWithConstraints(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                val previewWidth = maxWidth * 0.32f
                AndroidView(factory = { ctx -> SurfaceViewRenderer(ctx).apply { init(eglContext, null); setMirror(true) } }, update = { renderer -> localTrack.addSink(renderer) }, modifier = Modifier.size(previewWidth, previewWidth * 1.35f).clip(RoundedCornerShape(12.dp)))
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) { Text("VibeMeet call", style = MaterialTheme.typography.labelLarge); icebreakers.firstOrNull()?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) } }
        Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onMute) { Text("Mute") }; Button(onCamera) { Text("Camera") }; Button(onSwitch) { Text("Flip") }
                Button(onNext) { Text("NEXT") }; OutlinedButton(onEnd) { Text("End") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onReport) { Text("Report") }; TextButton(onBlock) { Text("Block") }; TextButton(onFavorite) { Text("♥ Favorite") }
            }
        }
    }
}

@Composable fun ProfileScreen(profile: PublicProfile?, onDelete: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        Text(profile?.displayName ?: "Profile", style = MaterialTheme.typography.headlineMedium)
        Text("${profile?.age ?: ""} · ${profile?.country ?: ""} · ${profile?.verificationStatus ?: ""}")
        Text(profile?.bio ?: ""); Text(profile?.interests?.joinToString(", ") ?: "")
        Text("Email, phone, exact location, IP and device info are never shown here or to other users.", modifier = Modifier.padding(vertical = 12.dp))
        OutlinedButton(onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("Log out") }
        TextButton(onDelete, modifier = Modifier.padding(top = 8.dp)) { Text("Delete my account") }
    }
}

@Composable fun ConnectionsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) { Text("Previous connections", style = MaterialTheme.typography.headlineMedium); Text("Your recent matches appear here from GET /session/history. Favorites and connection requests stay inside VibeMeet — no contact details are shared.", modifier = Modifier.padding(vertical = 12.dp)); OutlinedButton(onBack, shape = RoundedCornerShape(24.dp)) { Text("Back") } }
}
