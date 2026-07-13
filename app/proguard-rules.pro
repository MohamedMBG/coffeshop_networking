# ============================================================
# Project ProGuard / R8 configuration.
# Active when `release { isMinifyEnabled = true }` in build.gradle.kts.
# ============================================================

# Keep filename + line numbers in stack traces from Crashlytics / logcat.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed for Retrofit + Gson reflection).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ------------------------------------------------------------
# Firebase Firestore / Auth POJOs.
# Firestore deserializes documents via reflection into our model classes;
# fields must keep their names. Listing all models explicitly is more
# precise than a wildcard on com.example.**.
# ------------------------------------------------------------
-keep class com.example.loyaltyapp.models.** { *; }
-keepclassmembers class com.example.loyaltyapp.models.** { *; }
-keep class com.example.loyaltyapp.data.repository.RewardsRepository$RedemptionLog { *; }

# Keep no-arg constructors on POJOs (Firestore requirement).
-keepclassmembers class * {
    public <init>();
}

# Firebase internal: preserve everything to avoid surprise NoSuchMethodError.
-keep class com.google.firebase.** { *; }
-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ------------------------------------------------------------
# Retrofit + OkHttp + Gson
# ------------------------------------------------------------
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Our API DTOs (Gson reflects into them).
-keep class com.example.loyaltyapp.ApiService$* { *; }

# REST response envelopes. Top-level (not under .models, not nested in ApiService),
# so nothing above kept them and R8 renamed their fields — Gson then couldn't map
# {ok,data} / {ok,code,message}, so every REST write (earn/redeem) read as a failure
# and surfaced "Something went wrong". Keep the field names.
-keep class com.example.loyaltyapp.ApiResponse { *; }
-keep class com.example.loyaltyapp.ApiError { *; }

# ------------------------------------------------------------
# Glide
# ------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
-dontwarn com.bumptech.glide.**

# ------------------------------------------------------------
# ZXing (QR scanner) — uses reflection to load decoder backends.
# ------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# ------------------------------------------------------------
# AndroidX / Material — generally safe defaults are bundled with R8,
# but keep ViewBinding generated classes accessed by reflection.
# ------------------------------------------------------------
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(android.view.View);
}
