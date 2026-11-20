# PDFBox loads fonts/resources reflectively
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
# Tesseract JNI
-keep class com.googlecode.tesseract.android.** { *; }
