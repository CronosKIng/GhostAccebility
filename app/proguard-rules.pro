# Keep app working
-keep class com.security.update.** { *; }
-dontwarn com.security.update.**

# Obfuscate
-repackageclasses 'com.android.internal'
-allowaccessmodification
-overloadaggressively

# Remove logging
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

# Hide source
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Remove debug
-dontnote **
-dontwarn **
