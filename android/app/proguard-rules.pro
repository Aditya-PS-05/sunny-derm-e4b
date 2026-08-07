# Keep JNI entry points for the stable Sunny Offline native bridge.
-keep class com.sunny.skin.inference.SunnyMoeBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
