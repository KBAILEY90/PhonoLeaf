# R8 keep rules for the release build.
#
# READ THIS BEFORE TRIMMING ANYTHING. Every rule below protects something found
# at RUNTIME by name, which R8 cannot see and will happily rename or delete.
# The failure mode is not a build error: the app compiles, installs, launches,
# and then one feature does nothing. That is exactly the kind of bug this
# project has lost days to before, so each rule says why it exists.

# ---------------------------------------------------------------------------
# The speech engine. HIGHEST RISK IN THIS FILE.
# ---------------------------------------------------------------------------
# sherpa-onnx is a native library that calls back into these Kotlin classes
# over JNI, by exact class name and field/method signature. JNI lookups are
# strings resolved at runtime, so R8 sees no reference and is free to rename or
# strip them. If that happens, synthesis fails on device with no compile-time
# warning at all.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# Our own engine bridge. TtsService is named as a STRING in AndroidManifest
# (com.phonoleaf.ttsbridge.TtsService) and reached over AIDL; the generated
# Stub/Proxy classes are likewise resolved reflectively by Binder.
-keep class com.phonoleaf.ttsbridge.** { *; }

# ---------------------------------------------------------------------------
# Capacitor
# ---------------------------------------------------------------------------
# The bridge discovers plugins by annotation and invokes their methods by name
# from JavaScript. Nothing in the compiled Java references @PluginMethod bodies,
# so without these the app builds and every native call from the web layer
# silently fails.
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod <methods>;
}
-keep class * extends com.getcapacitor.Plugin { *; }

# Our plugins are registered by class literal in MainActivity, but their METHODS
# are still only reached from JS. Keep the whole app package: it is small, and
# the services here (PlaybackService, PackDownloadService) are named as strings
# in the manifest too.
-keep class com.phonoleaf.app.** { *; }

# ---------------------------------------------------------------------------
# Encrypted storage for the OAuth refresh token
# ---------------------------------------------------------------------------
# androidx.security-crypto sits on Tink, which resolves key managers and
# protobuf classes reflectively. Stripping them breaks reading the stored
# refresh token, which presents as an unexplained sign-out.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# ---------------------------------------------------------------------------
# Voice pack extraction
# ---------------------------------------------------------------------------
# commons-compress selects archive implementations by name and carries optional
# codec backends we do not ship. Keep the bzip2/tar paths and silence the rest.
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.bzip2.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**

# ---------------------------------------------------------------------------
# Play In-App Review
# ---------------------------------------------------------------------------
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# ---------------------------------------------------------------------------
# General
# ---------------------------------------------------------------------------
# Anything explicitly marked as reflectively reached.
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Native method holders: the JNI name must survive.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep source file and line numbers so a Play Console crash report is readable.
# Without this a stack trace from a released build is unusable for diagnosis.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations drive both Capacitor's plugin discovery and Tink's key managers.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
