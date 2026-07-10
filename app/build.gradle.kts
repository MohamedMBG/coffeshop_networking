plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.loyaltyapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.loyaltyapp"
        minSdk = 24
        targetSdk = 34
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
        buildConfigField(
            "String",
            "LEGACY_EMAIL_API_BASE_URL",
            "\"https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app/\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://bean-backend-ejzg.onrender.com/api/v1/\""
            )
            buildConfigField(
                "String",
                "LEGACY_EMAIL_API_BASE_URL",
                "\"https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app/\""
            )
        }
        release {
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
            buildConfigField(
                "String",
                "LEGACY_EMAIL_API_BASE_URL",
                "\"https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app/\""
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
