# JukeKart Release R8 / ProGuard keep rules

# Keep native JNI entrypoints and classes invoked from C++
-keep class com.mylittleblover.player2.platform.audio.NativeAudioLibrary {
    *;
}
-keep class com.mylittleblover.player2.platform.audio.source.NativeSourceCalls {
    *;
}
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep Android component entrypoints declared in AndroidManifest.xml
-keep class com.mylittleblover.mkwplayer.MainActivity { *; }
-keep class com.mylittleblover.mkwplayer.player.PlaybackService { *; }
-keep class com.mylittleblover.mkwplayer.pack.PackImportService { *; }
-keep class com.mylittleblover.mkwplayer.widget.PlayerWidgetProvider { *; }
-keep class com.mylittleblover.mkwplayer.widget.PlayerTileService { *; }

# Keep @Keep annotated symbols
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve readable stack traces for beta crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
