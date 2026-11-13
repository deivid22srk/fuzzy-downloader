# ---- Fuzzy Downloader / R8 full mode ----

# JNI (nome dos símbolos e interface usada pelo Rust)
-keep class com.fuzzydl.app.core.NativeDownloader { *; }
-keep interface com.fuzzydl.app.core.NativeCallback { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# org.json é parte do Android; kotlinx.coroutines/Coil trazem as próprias regras.
-dontwarn org.jetbrains.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
