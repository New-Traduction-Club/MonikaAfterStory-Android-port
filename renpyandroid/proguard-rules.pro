# ProGuard rules for Ren'Py Android

# Keep SDL and native methods
-keep class org.libsdl.app.** { *; }
-keepclassmembers class org.libsdl.app.** { *; }

# Keep RenPy activity and methods referenced from native code
-keep class org.renpy.android.** { *; }
-keepclassmembers class org.renpy.android.** { *; }

# Keep Pickle dependency
-keep class net.razorvine.pickle.** { *; }

# Ignore warnings from missing libraries that we don't strictly need at compile time
-dontwarn org.libsdl.app.**
-dontwarn org.renpy.android.**
-dontwarn net.razorvine.pickle.**
