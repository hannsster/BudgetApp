# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.mipresupuestocr.** {
    *** Companion;
}
-keepclasseswithmembers class com.mipresupuestocr.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mipresupuestocr.app.data.remote.**$$serializer { *; }
-keepclassmembers class com.mipresupuestocr.app.data.remote.** {
    *** Companion;
}

# Retrofit interfaces are used through reflection.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes Signature, Exceptions

# OkHttp platform hints.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
