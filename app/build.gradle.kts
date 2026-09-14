import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// Release signing credentials live in keystore.properties at the repo root, which is
// git-ignored (the keystore itself must never be committed). When the file is absent —
// CI, a fresh clone — the release build still assembles, just unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.loyaltyapp"
    compileSdk = 36

    defaultConfig {
        // Play rejects com.example.*, and applicationId is immutable once published.
        // Decoupled from `namespace` on purpose: the Java sources keep their original
        // package, only the shipped identity changes. The matching client must exist
        // in google-services.json or the Google Services plugin fails the build.
        applicationId = "com.beanloyal.customer"
        minSdk = 24
        targetSdk = 36
        // Must increase on every Play upload; Play rejects a versionCode it has seen before.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // P1: backend URL exposed via BuildConfig so debug/release can differ
        // and so the value lives in one place (here) rather than scattered in
        // ApiClient + TokenRegistrar.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"https://bean-backend-ejzg.onrender.com/api/v1/\""
        )
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://bean-backend-ejzg.onrender.com/api/v1/\""
            )
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // P1: enable R8 + resource shrinking for release builds. Keeps the
            // APK smaller and strips unreachable code. Proguard rules for the
            // Firebase / Retrofit / Glide / ZXing reflection paths live in
            // proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://bean-backend-ejzg.onrender.com/api/v1/\""
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.swiperefreshlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    // P1: Mockito 5.x makes inline mocking (final classes, static methods) the
    // default. Required because tests mock FirebaseAuth / FirebaseUser /
    // DocumentSnapshot, all of which are final. Mockito 4.x + mockito-inline
    // failed on JDK 21 with the bytebuddy class-file version mismatch that
    // sank the original test run.
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}
