# Strip Google Play Core (Flutter engine bundles these unnecessarily for F-Droid)
-dontwarn com.google.android.play.core.**
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**
-assumenosideeffects class com.google.android.play.core.** { *; }
-assumenosideeffects class com.google.android.play.core.splitinstall.** { *; }
-assumenosideeffects class com.google.android.play.core.tasks.** { *; }

# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# flutter_local_notifications
-keep class com.dexterous.flutterlocalnotifications.** { *; }
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# webview_flutter
-keep class io.flutter.plugins.webviewflutter.** { *; }

# Keystore and common
-keep class com.ujwal.focusgram.** { *; }

# Avoid stripping JS bridge names
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
