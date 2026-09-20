# Keep Firebase model reflection safe (we use org.json, not reflection, so minimal rules)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
