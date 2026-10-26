package com.vibemeet.app.data
import retrofit2.http.*
data class PublicProfile(val id: String, val username: String, val displayName: String, val age: Int, val country: String, val bio: String = "", val profilePhotoUrl: String? = null, val verificationStatus: String = "basic", val isPremium: Boolean = false, val interests: List<String> = emptyList(), val languages: List<String> = emptyList())
data class AuthResponse(val userId: String, val access: String, val refresh: String)
data class MatchFound(val sessionId: String, val peerId: String, val initiator: Boolean)
data class IceServersResponse(val iceServers: List<IceServerDto>)
data class IceServerDto(val urls: Any, val username: String? = null, val credential: String? = null)
interface VibeMeetApi {
    @POST("auth/login") suspend fun login(@Body body: Map<String, String>): AuthResponse
    @POST("auth/register") suspend fun register(@Body body: Map<String, Any>): AuthResponse
    @POST("auth/google") suspend fun google(@Body body: Map<String, String>): AuthResponse
    @POST("auth/google/complete") suspend fun googleComplete(@Body body: Map<String, Any>): AuthResponse
    @GET("profile") suspend fun myProfile(): PublicProfile
    @GET("profile/{id}") suspend fun profileById(@Path("id") id: String): PublicProfile
    @PUT("profile") suspend fun updateProfile(@Body body: Map<String, Any>): PublicProfile
    @POST("match/start") suspend fun startMatch(@Body body: Map<String, Any>): Map<String, String>
    @POST("match/cancel") suspend fun cancelMatch(): Map<String, String>
    @GET("match/ice-servers") suspend fun iceServers(): IceServersResponse
    @POST("session/end") suspend fun endSession(@Body body: Map<String, String>): Map<String, Boolean>
    @POST("favorite") suspend fun favorite(@Body body: Map<String, String>): Map<String, Boolean>
    @POST("block") suspend fun block(@Body body: Map<String, String>): Map<String, Boolean>
    @POST("report") suspend fun report(@Body body: Map<String, String?>): Map<String, String>
    @GET("session/history") suspend fun history(): List<Map<String, Any?>>
    @GET("coins") suspend fun coins(): Map<String, Int>
    @POST("coins/purchase") suspend fun verifyCoinPurchase(@Body body: Map<String, String>): Map<String, Any>
    @POST("subscription/verify") suspend fun verifySubscription(@Body body: Map<String, String>): Map<String, Any>
    @GET("subscription") suspend fun subscription(): Map<String, Any>
    @DELETE("auth/account") suspend fun deleteAccount(): Map<String, Boolean>
}
