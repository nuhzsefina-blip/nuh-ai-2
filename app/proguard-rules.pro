# Redat Dagna R8 / ProGuard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**

# ZXing
-keep class com.google.zxing.** { *; }

# Moshi / JSON
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Room & FTS
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Firebase AI Logic, Ktor & Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-keep class com.google.firebase.ai.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    *** $serializer;
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }

