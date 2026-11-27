# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gugachat.app.core.storage.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.core.storage.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.gugachat.app.data.api.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.data.api.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.gugachat.app.data.local.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.data.local.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.gugachat.app.domain.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.domain.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.gugachat.app.ui.providers.**$$serializer { *; }
-keepclassmembers class com.gugachat.app.ui.providers.** {
    *** Companion;
}

# Keep enum values used by kotlinx.serialization & Room
-keepclassmembers enum com.gugachat.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ONNX Runtime Mobile JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# PDFBox Android & BouncyCastle
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.google.errorprone.annotations.**

# WebView JS bridge (RpHtmlCardWebView)
-keepclassmembers class com.gugachat.app.ui.chat.** {
    @android.webkit.JavascriptInterface <methods>;
}
