# Add project specific ProGuard rules here.
# Keep Retrofit models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.fuelify.data.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
