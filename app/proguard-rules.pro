# PDFBox loads fonts/resources reflectively
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
# Tesseract JNI
-keep class com.googlecode.tesseract.android.** { *; }

# WorkManager (pulled in by Glance widgets) builds its Room database by reflection.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
