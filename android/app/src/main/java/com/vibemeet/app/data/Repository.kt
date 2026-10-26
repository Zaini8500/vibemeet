package com.vibemeet.app.data
import javax.inject.Inject
import javax.inject.Singleton
/** Single place the UI talks to the backend. Every call goes through Retrofit
 *  with the JWT interceptor (see di/AppModule). No screen calls Retrofit directly,
 *  and no screen invents a profile, match or session. */
@Singleton class VibeMeetRepository @Inject constructor(private val api: VibeMeetApi, private val tokens: TokenStore) {
    suspend fun login(email: String, password: String): AuthResponse {
        val r = api.login(mapOf("email" to email, "password" to password)); tokens.save(r.access, r.refresh); return r
    }
    suspend fun register(body: Map<String, Any>): AuthResponse {
        val r = api.register(body); tokens.save(r.access, r.refresh); return r
    }
    suspend fun google(idToken: String): AuthResponse {
        val r = api.google(mapOf("idToken" to idToken)); tokens.save(r.access, r.refresh); return r
    }
    suspend fun googleComplete(idToken: String, body: Map<String, Any>): AuthResponse {
        val r = api.googleComplete(body + mapOf("idToken" to idToken)); tokens.save(r.access, r.refresh); return r
    }
    suspend fun myProfile(): PublicProfile = api.myProfile()
    suspend fun profileById(id: String): PublicProfile = api.profileById(id)
    suspend fun updateProfile(body: Map<String, Any>): PublicProfile = api.updateProfile(body)
    suspend fun startMatch(preferences: Map<String, Any> = emptyMap()): Map<String, String> = api.startMatch(mapOf("preferences" to preferences))
    suspend fun cancelMatch() = api.cancelMatch()
    suspend fun iceServers(): IceServersResponse = api.iceServers()
    suspend fun endSession(sessionId: String) = api.endSession(mapOf("sessionId" to sessionId))
    suspend fun favorite(peerId: String) = api.favorite(mapOf("favoriteId" to peerId))
    suspend fun block(peerId: String) = api.block(mapOf("blockedId" to peerId))
    suspend fun report(peerId: String, category: String, sessionId: String?) = api.report(mapOf("reportedUserId" to peerId, "category" to category, "sessionId" to sessionId))
    suspend fun history() = api.history()
    suspend fun coins() = api.coins()
    /** Server verification only. Returns the backend's verified result; the
     *  caller must not grant anything on a local Play callback alone. */
    suspend fun verifyCoinPurchase(productId: String, purchaseToken: String) = api.verifyCoinPurchase(mapOf("productId" to productId, "purchaseToken" to purchaseToken))
    suspend fun verifySubscription(productId: String, purchaseToken: String) = api.verifySubscription(mapOf("productId" to productId, "purchaseToken" to purchaseToken))
    suspend fun logout() = tokens.clear()
    suspend fun deleteAccount() { api.deleteAccount(); tokens.clear() }
}
