# Keep app functional
-keep class com.security.update.** { *; }
-dontwarn com.security.update.**

# Aggressive obfuscation
-repackageclasses 'com.android.wifi'
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

# Remove all logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** e(...);
    public static *** w(...);
}

# Keep JSON
-keepclassmembers class * {
    @org.json.JSONObject <init>(...);
    @org.json.JSONArray <init>(...);
}

# Hide source file names
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Remove debug info completely
-dontnote **
-dontwarn **

# Anti-Play Protect specific
-keepattributes Exceptions,InnerClasses
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
