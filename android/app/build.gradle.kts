plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.google.dagger.hilt.android"); id("kotlin-kapt") }
android {
    namespace = "com.vibemeet.app"; compileSdk = 35
    defaultConfig {
        applicationId = "com.vibemeet.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0.0"
        // MOCK_MODE is for UI development only. It must be false in any Play build.
        buildConfigField("boolean", "MOCK_MODE", "false")
        // Google Cloud Web OAuth client ID for VibeMeet (project vibemeet-512621,
        // account zaintahir7000@gmail.com, created Oct 25, 2026). This ID is
        // not a secret — it is embedded in the app by design.
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"395071022050-j6bh5mpkbripkmnv2kr8ehtlh1ioplvs.apps.googleusercontent.com\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.dagger:hilt-android:2.51.1"); kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0"); implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.1")
    // org.webrtc:google-webrtc is no longer published on Maven Central (0 results);
    // io.github.webrtc-sdk:android ships the same org.webrtc package (M114, close
    // to the original M106-era code) and is on Maven Central.
    implementation("io.github.webrtc-sdk:android:114.5735.11")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.android.billingclient:billing-ktx:7.0.0")
    // play-services-ads intentionally NOT included: AdMob is not used in any
    // screen yet, and its auto-init provider requires an AdMob APPLICATION_ID
    // in the manifest — without one it can crash the app at launch.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
