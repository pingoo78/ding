# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Classes @Serializable de l'app (domaine, moteur persisté, data) et leurs serializers générés
-keep,includedescriptorclasses class fr.pingooweb.ding.**$$serializer { *; }
-keepclassmembers class fr.pingooweb.ding.** {
    *** Companion;
}
-keepclasseswithmembers class fr.pingooweb.ding.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Polymorphisme SoundChoice (objets et data class) + RunState / Program / Step
-keep class fr.pingooweb.ding.domain.model.** { *; }
-keep class fr.pingooweb.ding.domain.engine.** { *; }
-keep class fr.pingooweb.ding.data.** { *; }

# Composants du manifest
-keep class fr.pingooweb.ding.DingApp
-keep class fr.pingooweb.ding.MainActivity
-keep class fr.pingooweb.ding.alert.AlertActivity
-keep class fr.pingooweb.ding.runtime.TimerService
-keep class fr.pingooweb.ding.runtime.AlarmReceiver
-keep class fr.pingooweb.ding.runtime.NotificationActionReceiver
