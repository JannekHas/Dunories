# R8-Regeln für die Release-APK (die Bibliotheken bringen ihre eigenen Regeln mit).

# kotlinx.serialization: Navigations-Routen, Sicherungsformat und Wochenauswertung werden über
# generierte Serializer gelesen – Klassen mit @Serializable samt Serializer behalten.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class de.kalorienapp.**$$serializer { *; }
-keepclassmembers class de.kalorienapp.** {
    *** Companion;
}
-keepclasseswithmembers class de.kalorienapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class de.kalorienapp.** { *; }

# Stacktraces in Absturzmeldungen lesbar halten
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit (Barcode) + Google-Play-Dienste-Basis: R8 hat hier interne Felder entfernt → Absturz beim
# App-Start („RootTelemetryConfigManager“, NullPointerException). Diese Bibliotheken unverändert lassen.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.datatransport.** { *; }
