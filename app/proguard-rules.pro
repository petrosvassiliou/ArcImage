# Keep 7-Zip-JBinding JNI callbacks and native bridge classes
-keep class net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**

# Keep Zip4j headers and crypto models
-keep class net.lingala.zip4j.** { *; }

# Keep Commons Compress & XZ reflection-loaded codecs; ignore optional unused codecs (zstd/brotli/pack200)
-keep class org.tukaani.xz.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.osgi.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn com.google.android.play.**
