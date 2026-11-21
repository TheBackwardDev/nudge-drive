# kotlinx.serialization: keep generated serializers of our @Serializable models.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.nudge.drive.** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.nudge.drive.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nudge.drive.**$$serializer { *; }

# WebView bridge used by the in-app editor.
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# WorkManager instantiates the backup worker by name.
-keep class com.nudge.drive.data.BackupWorker { <init>(...); }

# OkHttp / Okio optional platform classes.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Credential Manager loads its Play services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
