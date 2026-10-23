# Keep ML Kit and DataTransport classes for OCR
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**
-dontwarn com.google.android.gms.**

# PDF text layer
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
# On-device vision-language model (native bindings)
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
