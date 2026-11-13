# ---- Fuzzy Downloader / R8 full mode ----

# JNI (nome dos símbolos e classe usada pelo Rust)
-keep class com.fuzzydl.app.core.NativeDownloader { *; }
-keep interface com.fuzzydl.app.core.NativeCallback { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# UserService do Shizuku: instanciado por reflexão no processo shell
-keep class com.fuzzydl.app.shizuku.FileService { *; }
-keep class com.fuzzydl.app.shizuku.FileService$* { *; }

# AIDL gerado
-keep class com.fuzzydl.app.shizuku.IFileService { *; }
-keep class com.fuzzydl.app.shizuku.IFileService$* { *; }
-keep class com.fuzzydl.app.shizuku.IExtractCallback { *; }
-keep class com.fuzzydl.app.shizuku.IExtractCallback$* { *; }
-keepclassmembers class * implements android.os.IInterface {
    public static ** asInterface(android.os.IBinder);
}

# Shizuku
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# org.json é parte do Android; nada a fazer. kotlinx.coroutines já traz regras.
-dontwarn org.jetbrains.annotations.**
