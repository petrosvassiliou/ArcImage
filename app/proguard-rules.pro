# Keep 7-Zip-JBinding JNI callbacks and native bridge classes
-keep class net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**

# Keep Zip4j headers and crypto models
-keep class net.lingala.zip4j.** { *; }

# Keep Commons Compress & XZ reflection-loaded codecs
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.**
