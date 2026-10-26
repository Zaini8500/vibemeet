package com.vibemeet.app.data
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.dataStore by preferencesDataStore("vibemeet_auth")
/** Tokens live in DataStore, never in plain SharedPreferences or logs.
 *  Access token is 15 minutes; refresh token is used only to get a new one. */
@Singleton class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore
    private val ACCESS = stringPreferencesKey("access"); private val REFRESH = stringPreferencesKey("refresh")
    val accessToken: Flow<String?> = store.data.map { it[ACCESS] }
    suspend fun save(access: String, refresh: String) { store.edit { it[ACCESS] = access; it[REFRESH] = refresh } }
    suspend fun clear() { store.edit { it.remove(ACCESS); it.remove(REFRESH) } }
}
