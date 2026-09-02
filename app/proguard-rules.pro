# Add project specific ProGuard rules here.
# Kotlin serialization keeps its own consumer rules; Retrofit/OkHttp ship consumer rules too.

-keepattributes Signature
-keepattributes *Annotation*

# kotlinx.serialization
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
