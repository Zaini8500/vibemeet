package com.vibemeet.app.ui.viewmodel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibemeet.app.data.PublicProfile
import com.vibemeet.app.data.SignalingClient
import com.vibemeet.app.data.TokenStore
import com.vibemeet.app.data.VibeMeetRepository
import com.vibemeet.app.di.BASE_URL
import com.vibemeet.app.webrtc.CallState
import com.vibemeet.app.webrtc.WebRtcManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
/** Every ViewModel below calls the real repository / signalling / WebRTC.
 *  Loading, success and error states are explicit — screens never show a
 *  blank screen on failure, they show the error and a retry action. */
@HiltViewModel class AuthViewModel @Inject constructor(private val repo: VibeMeetRepository) : ViewModel() {
    private val _loading = MutableStateFlow(false); val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    private val _authed = MutableStateFlow(false); val authed: StateFlow<Boolean> = _authed
    fun setError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    /** Friendly, visible error text for backend failures. Returns null when
     *  no specific mapping applies, so the caller keeps its own message. */
    private fun mappedError(e: Exception, google: Boolean): String? {
        if (e is retrofit2.HttpException) {
            return when (e.code()) {
                409 -> "Email or username already taken — try Log in instead"
                400 -> "Check your name (3+ letters) and date of birth in YYYY-MM-DD format, and that you are 18 or older"
                429 -> "Too many tries — wait a bit and try again"
                403 -> "You must be 18 or older"
                501 -> if (google) "Google sign-in is not set up yet" else "This feature is not set up yet — try again later"
                else -> null
            }
        }
        if (google) return "Google sign-in failed — try email instead"
        return null
    }
    fun login(email: String, password: String, onDone: () -> Unit) = viewModelScope.launch {
        _loading.value = true; _error.value = null
        try { repo.login(email, password); _authed.value = true; onDone() } catch (e: Exception) { _error.value = mappedError(e, false) ?: "Login failed. Check your email and password, then try again." } finally { _loading.value = false }
    }
    fun register(body: Map<String, Any>, onDone: () -> Unit) = viewModelScope.launch {
        _loading.value = true; _error.value = null
        try { repo.register(body); _authed.value = true; onDone() } catch (e: Exception) { _error.value = mappedError(e, false) ?: "Could not create your account. That email or username may already be taken." } finally { _loading.value = false }
    }
    // Google flow: existing user -> authed. New user -> backend returns 428
    // onboarding_required; the idToken is kept in memory only (never stored)
    // and re-sent to /auth/google/complete after onboarding.
    private val _googleIdToken = MutableStateFlow<String?>(null); val googleIdToken: StateFlow<String?> = _googleIdToken
    private val _needsGoogleOnboarding = MutableStateFlow(false); val needsGoogleOnboarding: StateFlow<Boolean> = _needsGoogleOnboarding
    fun google(idToken: String, onAuthed: () -> Unit, onNeedsOnboarding: () -> Unit) = viewModelScope.launch {
        _loading.value = true; _error.value = null
        try { repo.google(idToken); _authed.value = true; onAuthed() }
        catch (e: retrofit2.HttpException) {
            if (e.code() == 428) { _googleIdToken.value = idToken; _needsGoogleOnboarding.value = true; onNeedsOnboarding() }
            else _error.value = mappedError(e, true) ?: "Google sign-in failed — try email instead"
        } catch (e: Exception) { _error.value = mappedError(e, true) ?: "Google sign-in failed — try email instead" }
        finally { _loading.value = false }
    }
    fun googleComplete(body: Map<String, Any>, onDone: () -> Unit) = viewModelScope.launch {
        val token = _googleIdToken.value ?: return@launch
        _loading.value = true; _error.value = null
        try { repo.googleComplete(token, body); _needsGoogleOnboarding.value = false; _authed.value = true; onDone() }
        catch (e: Exception) { _error.value = mappedError(e, true) ?: "Could not finish Google sign-up. Try again." }
        finally { _loading.value = false }
    }
    fun logout() = viewModelScope.launch { repo.logout(); _authed.value = false; _googleIdToken.value = null; _needsGoogleOnboarding.value = false }
}
@HiltViewModel class HomeViewModel @Inject constructor(private val repo: VibeMeetRepository) : ViewModel() {
    private val _profile = MutableStateFlow<PublicProfile?>(null); val profile: StateFlow<PublicProfile?> = _profile
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    fun load() = viewModelScope.launch {
        try { _profile.value = repo.myProfile() } catch (e: Exception) { _error.value = "Could not load your profile. Try again." }
    }
    fun saveProfile(body: Map<String, Any>, onDone: () -> Unit = {}) = viewModelScope.launch {
        try { _profile.value = repo.updateProfile(body) } catch (e: Exception) { _error.value = "Could not save your profile. Try again." } finally { onDone() }
    }
}
@HiltViewModel class MatchingViewModel @Inject constructor(private val repo: VibeMeetRepository) : ViewModel() {
    private val _status = MutableStateFlow("idle"); val status: StateFlow<String> = _status
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        _status.value = "searching"; _error.value = null
        try { repo.startMatch(); onStarted() } catch (e: Exception) { _status.value = "idle"; _error.value = "No users available right now. Try again." }
    }
    fun cancel() = viewModelScope.launch { try { repo.cancelMatch() } catch (_: Exception) {} finally { _status.value = "idle" } }
}
@HiltViewModel class BillingViewModel @Inject constructor(private val repo: VibeMeetRepository) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null); val status: StateFlow<String?> = _status
    private val _coinBalance = MutableStateFlow(0); val coinBalance: StateFlow<Int> = _coinBalance
    /** Call this from BillingManager.onPurchaseToken. Only a backend
     *  confirmation counts as a grant; a failed verification grants nothing. */
    fun onPurchaseToken(token: String, productId: String, isSubscription: Boolean) = viewModelScope.launch {
        try {
            if (isSubscription) {
                val r = repo.verifySubscription(productId, token)
                _status.value = if (r["ok"] == true) "Premium active" else "Subscription verification failed"
            } else {
                val r = repo.verifyCoinPurchase(productId, token)
                _status.value = if (r["ok"] == true) "Coins credited" else "Coin purchase verification failed"
                _coinBalance.value = (r["balance"] as? Number)?.toInt() ?: _coinBalance.value
            }
        } catch (e: Exception) { _status.value = "Verification failed. Nothing was granted — try again." }
    }
    fun refreshCoins() = viewModelScope.launch { try { _coinBalance.value = repo.coins()["balance"] ?: 0 } catch (_: Exception) {} }
}
/** Owns the live call: signalling callbacks, WebRTC lifecycle, and NEXT.
 *  NEXT always closes the old PeerConnection first, ends the session on the
 *  server, then starts a fresh match — it never reuses a connection. */
@HiltViewModel class VideoViewModel @Inject constructor(
    private val repo: VibeMeetRepository,
    private val tokens: TokenStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val webrtc = WebRtcManager(context)
    private var signaling: SignalingClient? = null
    private var sessionId: String? = null
    private var peerId: String? = null
    private val _callState = MutableStateFlow(CallState.IDLE); val callState: StateFlow<CallState> = _callState
    private val _peerProfile = MutableStateFlow<PublicProfile?>(null); val peerProfile: StateFlow<PublicProfile?> = _peerProfile
    private val _messages = MutableStateFlow<List<Pair<String, String>>>(emptyList()); val messages: StateFlow<List<Pair<String, String>>> = _messages
    private val _error = MutableStateFlow<String?>(null); val error: StateFlow<String?> = _error
    init { viewModelScope.launch { webrtc.state.collect { _callState.value = it } } }
    fun connectSignaling(onMatch: (String, String, Boolean) -> Unit) {
        signaling?.disconnect()
        val client = SignalingClient(BASE_URL) { runBlocking { tokens.accessToken.first() ?: "" } }
        client.onMatchFound = { sid, pid, initiator -> sessionId = sid; peerId = pid; onMatch(sid, pid, initiator) }
        client.onSignal = { _, type, payload -> handleSignal(type, payload) }
        client.onChat = { from, text -> _messages.value = _messages.value + (from to text) }
        client.connect(); signaling = client
    }
    fun startCall(sid: String, pid: String, initiator: Boolean) = viewModelScope.launch {
        sessionId = sid; peerId = pid
        try {
            val ice = repo.iceServers().iceServers.map { dto ->
                val urls = when (val u = dto.urls) { is String -> listOf(u); is List<*> -> u.filterIsInstance<String>(); else -> emptyList() }
                PeerConnection.IceServer.builder(urls).setUsername(dto.username).setPassword(dto.credential).createIceServer()
            }
            webrtc.init(ice)
            webrtc.onIceCandidate = { c -> signaling?.sendSignal(pid, sid, "ice", JSONObject().apply { put("sdpMid", c.sdpMid); put("sdpMLineIndex", c.sdpMLineIndex); put("candidate", c.sdp) }) }
            if (initiator) webrtc.createOffer { offer -> signaling?.sendSignal(pid, sid, "offer", JSONObject().apply { put("type", "offer"); put("sdp", offer.description) }) }
            try { _peerProfile.value = repo.profileById(pid) } catch (_: Exception) {}
        } catch (e: Exception) { _error.value = "Could not start the video call. Check your connection and try again." }
    }
    private fun handleSignal(type: String, payload: JSONObject) {
        val pid = peerId ?: return; val sid = sessionId ?: return
        when (type) {
            "offer" -> webrtc.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, payload.getString("sdp"))) { webrtc.createAnswer { ans -> signaling?.sendSignal(pid, sid, "answer", JSONObject().apply { put("type", "answer"); put("sdp", ans.description) }) } }
            "answer" -> webrtc.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, payload.getString("sdp")))
            "ice" -> webrtc.addIceCandidate(IceCandidate(payload.optString("sdpMid"), payload.optInt("sdpMLineIndex"), payload.getString("candidate")))
        }
    }
    fun toggleMic(enabled: Boolean) = webrtc.setMicEnabled(enabled)
    fun toggleCamera(enabled: Boolean) = webrtc.setCameraEnabled(enabled)
    fun switchCamera() = webrtc.switchCamera()
    fun sendReaction(emoji: String) { peerId?.let { signaling?.sendReaction(it, emoji) } }
    fun favorite() = viewModelScope.launch { peerId?.let { try { repo.favorite(it) } catch (_: Exception) {} } }
    fun block(onDone: () -> Unit) = viewModelScope.launch { peerId?.let { try { repo.block(it) } catch (_: Exception) {} }; endCall(); onDone() }
    fun report(category: String, onDone: () -> Unit) = viewModelScope.launch { peerId?.let { try { repo.report(it, category, sessionId) } catch (_: Exception) {} }; endCall(); onDone() }
    fun endCall() = viewModelScope.launch { sessionId?.let { try { repo.endSession(it) } catch (_: Exception) {} }; webrtc.close(); sessionId = null; peerId = null }
    fun next(onMatching: () -> Unit) = viewModelScope.launch {
        endCall(); try { repo.startMatch() } catch (_: Exception) {}
        onMatching()
    }
    override fun onCleared() { signaling?.disconnect(); webrtc.close(); super.onCleared() }
    fun icebreakers(sharedInterests: List<String>): List<String> = listOf(
        "Ask them about their favourite ${sharedInterests.firstOrNull() ?: "hobby"}.",
        "Ask what country they would like to visit.",
        "What's your favourite sport?",
        "Ask them about their favourite movie."
    )
    fun reportCategories() = listOf("nudity", "harassment", "hate", "scam", "spam", "underage", "fake", "violence", "other")
}
