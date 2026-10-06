# kotlinx.serialization: keep generated serializers for the API models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.dailyrupi.core.model.**$$serializer { *; }
-keepclassmembers class com.dailyrupi.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.dailyrupi.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces with suspend functions.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.dailyrupi.core.net.DailyRupiApi { *; }
