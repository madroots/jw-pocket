# Applied to both apps. Keeps kotlinx.serialization working on the API models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keep,includedescriptorclasses class app.jwpocket.core.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
