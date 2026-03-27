# Keep RenPy entry points and JNI-referenced classes in release app module
-keep class org.renpy.android.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.jnius.** { *; }
