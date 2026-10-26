package com.vibemeet.app.di
import android.content.Context
import com.vibemeet.app.data.TokenStore
import com.vibemeet.app.data.VibeMeetApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
/** Base URL: change this to your deployed backend before a release build.
 *  Do NOT point a release build at localhost — a phone cannot reach it. */
const val BASE_URL = "http://10.0.2.2:8080/"
@Module @InstallIn(SingletonComponent::class) object AppModule {
    @Provides @Singleton fun provideTokenStore(@ApplicationContext ctx: Context): TokenStore = TokenStore(ctx)
    @Provides @Singleton fun provideApi(tokens: TokenStore): VibeMeetApi {
        val auth = Interceptor { chain ->
            // Synchronous read is acceptable here because DataStore is the
            // single source of truth and the value is cached after first read.
            val token = runBlocking { tokens.accessToken.first() }
            val req = if (token != null) chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build() else chain.request()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder().addInterceptor(auth).build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(VibeMeetApi::class.java)
    }
}
