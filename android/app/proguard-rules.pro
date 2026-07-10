# Keep JNI entry points for the llama.cpp / mtmd native bridge.
-keep class com.sunny.skin.inference.LlamaBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
