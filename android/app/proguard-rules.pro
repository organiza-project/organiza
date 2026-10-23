# Flutter core and plugins
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Preserve native JNI methods for SQLite and system channels
-keepclasseswithmembernames class * {
    native <methods>;
}

# Drift & SQLite
-dontwarn org.sqlite.**
-keep class org.sqlite.** { *; }

# Android UsageStats
-dontwarn android.app.usage.**

# JNI & Reflection preservation
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
