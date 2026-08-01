# Keep JNI entry points for the Sunny-MoE native bridge.
-keep class com.sunny.skin.inference.SunnyMoeBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
