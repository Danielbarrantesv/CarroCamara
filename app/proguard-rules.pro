# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation,allowshrinking class * extends androidx.lifecycle.ViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# CameraX
-dontwarn androidx.camera.**

# NanoHTTPD
-keep class org.nanohttpd.** { *; }
-keep class fi.iki.elonen.** { *; }

# iText PDF
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep app data models
-keep class com.danielbarrantesv.dashcam.data.model.** { *; }
