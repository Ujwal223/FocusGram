# FocusGram R8 / ProGuard Optimization Rules
# Ensures GeckoView JNI, WebExtensions, Room, and Compose reflection safety

# 1. Mozilla GeckoView Native Engine & JNI
-keep class org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep interface org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.geckoview.**
-dontwarn org.mozilla.gecko.**

# Keep WebExtension classes and message serialization delegates
-keepclassmembers class org.mozilla.geckoview.WebExtension$** { *; }
-keepclassmembers class org.mozilla.geckoview.GeckoSession$** { *; }

# 2. Kotlin Coroutines & Reflection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**

# 3. Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-keep class com.focusgram.android.core.database.** { *; }
-dontwarn androidx.room.paging.**

# 4. AndroidX DataStore & Security Crypto (Keystore)
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# 5. Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# 6. WebView JavascriptInterface Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
