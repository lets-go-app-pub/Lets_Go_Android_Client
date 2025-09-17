# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep protobuf file names
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep protobuf file names
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep maps support stuff
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepnames class com.google.android.gms.maps.SupportMapFragment

# Prints all proguard configurations used, including those used by other libraries.
# Can specify any path and filename.
-printconfiguration /home/jeremiah/AndroidStudioProjects/Lets_Go_Projects/LetsGo/full-r8-config.txt

# Don't post warnings about javax.naming missing (this was recommended by android?)
-dontwarn javax.naming.**

# This will strip `Log.v`, `Log.d`, and `Log.i` statements and will leave `Log.w` and `Log.e` statements intact.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}