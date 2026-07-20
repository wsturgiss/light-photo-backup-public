import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt"); id("org.jetbrains.kotlin.plugin.serialization") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
val defaultServer = providers.gradleProperty("PHOTO_BACKUP_AUTH_SERVER_URL").orNull.orEmpty()
val debugServer = local.getProperty("PHOTO_BACKUP_AUTH_SERVER_URL", defaultServer)
android {
    namespace = "com.stan.lightphotobackup"; compileSdk = 36
    defaultConfig { applicationId = "com.stan.lightphotobackup"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildTypes {
        debug { buildConfigField("String", "AUTH_SERVER_BASE_URL", "\"${debugServer.replace("\"", "\\\"")}\"") }
        release { isMinifyEnabled = true; buildConfigField("String", "AUTH_SERVER_BASE_URL", "\"\""); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    sourceSets["debug"].res.srcDir("src/debug/res")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00")); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.foundation:foundation"); implementation("androidx.compose.material3:material3"); debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.2"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1"); implementation("androidx.datastore:datastore-preferences:1.1.1"); implementation("com.squareup.okhttp3:okhttp:4.12.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
kapt { correctErrorTypes = true }
