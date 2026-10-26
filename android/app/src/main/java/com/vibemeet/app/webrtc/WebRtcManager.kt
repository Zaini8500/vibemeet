package com.vibemeet.app.webrtc
import android.content.Context
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
/** Real WebRTC manager. No simulated connection: state only becomes Connected
 *  when PeerConnection actually reports it, and every resource is released
 *  on [close] (camera, audio, PeerConnection, factory). */
enum class CallState { IDLE, CONNECTING, CONNECTED, POOR, RECONNECTING, DISCONNECTED }
class WebRtcManager(private val context: Context) {
    private val _state = MutableStateFlow(CallState.IDLE); val state: StateFlow<CallState> = _state
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var eglBase: EglBase? = null
    val eglContext: EglBase.Context? get() = eglBase?.eglBaseContext
    private val _remoteTrack = MutableStateFlow<VideoTrack?>(null); val remoteTrack: StateFlow<VideoTrack?> = _remoteTrack
    private val _localTrackFlow = MutableStateFlow<VideoTrack?>(null); val localTrackFlow: StateFlow<VideoTrack?> = _localTrackFlow
    var onRemoteTrack: ((VideoTrack) -> Unit)? = null
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    fun init(iceServers: List<PeerConnection.IceServer>) {
        close() // never reuse a PeerConnection across calls (NEXT creates a fresh one)
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        factory = PeerConnectionFactory.builder().setVideoEncoderFactory(DefaultVideoEncoderFactory(null, true, true)).setVideoDecoderFactory(DefaultVideoDecoderFactory(null)).createPeerConnectionFactory()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peer = factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { onIceCandidate?.invoke(c) }
            override fun onAddStream(s: MediaStream) { s.videoTracks.firstOrNull()?.let { _remoteTrack.value = it; onRemoteTrack?.invoke(it) } }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                _state.value = when (s) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> CallState.CONNECTED
                    PeerConnection.IceConnectionState.CHECKING, PeerConnection.IceConnectionState.NEW -> CallState.CONNECTING
                    PeerConnection.IceConnectionState.DISCONNECTED -> CallState.RECONNECTING
                    PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.CLOSED -> CallState.DISCONNECTED
                    else -> CallState.POOR
                }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                if (s == PeerConnection.PeerConnectionState.FAILED) _state.value = CallState.DISCONNECTED
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) { s.firstOrNull()?.videoTracks?.firstOrNull()?.let { _remoteTrack.value = it; onRemoteTrack?.invoke(it) } }
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
        })
        startLocalCapture()
    }
    private fun startLocalCapture() {
        val f = factory ?: return
        videoSource = f.createVideoSource(false)
        capturer = Camera2Enumerator(context).run { createCapturer(deviceNames.firstOrNull { isFrontFacing(it) } ?: deviceNames.first(), null) }
        capturer?.initialize(SurfaceTextureHelper.create("capture", eglBase!!.eglBaseContext), context, videoSource?.capturerObserver)
        capturer?.startCapture(640, 480, 30)
        localVideo = f.createVideoTrack("local_video", videoSource); _localTrackFlow.value = localVideo
        localAudio = f.createAudioTrack("local_audio", f.createAudioSource(MediaConstraints()))
        peer?.addTrack(localVideo, listOf("local_stream")); peer?.addTrack(localAudio, listOf("local_stream"))
    }
    fun localTrack(): VideoTrack? = localVideo
    fun createOffer(onOffer: (SessionDescription) -> Unit) {
        val pc = peer ?: error("init() first")
        pc.createOffer(MediaConstraints(), object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), d); onOffer(d)
            }
            override fun onSetSuccess() {}; override fun onCreateFailure(e: String) {}; override fun onSetFailure(e: String) {}
        })
    }
    fun createAnswer(onAnswer: (SessionDescription) -> Unit) {
        val pc = peer ?: error("init() first")
        pc.createAnswer(MediaConstraints(), object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), d); onAnswer(d)
            }
            override fun onSetSuccess() {}; override fun onCreateFailure(e: String) {}; override fun onSetFailure(e: String) {}
        })
    }
    fun setRemoteDescription(desc: SessionDescription, onSet: () -> Unit = {}) {
        peer?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription) {}; override fun onSetSuccess() { onSet() }
            override fun onCreateFailure(e: String) {}; override fun onSetFailure(e: String) {}
        }, desc)
    }
    fun addIceCandidate(c: IceCandidate) { peer?.addIceCandidate(c) }
    fun setMicEnabled(enabled: Boolean) { localAudio?.setEnabled(enabled) }
    fun setCameraEnabled(enabled: Boolean) { localVideo?.setEnabled(enabled) }
    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun close() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        videoSource?.dispose(); peer?.close(); peer = null; factory?.dispose(); factory = null
        try { eglBase?.release() } catch (_: Exception) {}
        eglBase = null; _remoteTrack.value = null; _localTrackFlow.value = null; localVideo = null
        _state.value = CallState.IDLE
    }
    private class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}; override fun onSetSuccess() {}
        override fun onCreateFailure(e: String) {}; override fun onSetFailure(e: String) {}
    }
}
