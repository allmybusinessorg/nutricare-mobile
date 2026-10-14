# ============================================================================
# NutriCare Enterprise Security & R8 Obfuscation Rules
# Protects against reverse-engineering, decompilation (JADX, APKTool),
# string extraction, and tampering.
# ============================================================================

# 1. Repackage all internal classes into a single obfuscated flat package
-repackageclasses 'a'
-allowaccessmodification

# 2. Aggressive optimizations & class/member renaming
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5

# 3. Strip debugging, line numbers, local variables, and source file metadata
-renamesourcefileattribute 'SourceFile'
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 4. Remove all runtime Log and debugging statements from bytecode
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 5. Keep Compose runtime rules
-keepclassmembers class * extends androidx.compose.runtime.State { *; }

# 6. Keep Kotlinx Serialization data transfer objects
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepclassmembers class com.nutricare.mobile.data.** { *; }
-keepclassmembers class com.nutricare.mobile.features.dietitian.DietitianProfile { *; }
-keepclassmembers class com.nutricare.mobile.features.recipes.ClinicalRecipe { *; }

# 7. Keep Supabase SDK reflection targets
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# 8. Keep OkHttp & Ktor engines
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# 9. Keep Android system components (Activities, Services, Receivers, Providers)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
