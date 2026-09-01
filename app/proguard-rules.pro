# sherpa-onnx 走 JNI，保留 native 相关类与 onnxruntime
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }

# 通用
-keepattributes Signature, *Annotation*, InnerClasses
