# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gugachat.app.data.api.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.data.api.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.gugachat.app.domain.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.domain.** {
    *** Companion;
}
