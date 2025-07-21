# Mantener clases de modelos usados en Room y Gson (ajusta el paquete si usas otro)
-keep class com.rayoai.data.local.model.CaptureEntity { *; }
-keep class com.rayoai.domain.model.ChatMessage { *; }

# Mantener todas las clases de Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.TypeConverter { *; }
-keep class * extends androidx.room.Entity { *; }
-keep class * extends androidx.room.Dao { *; }

# Mantener los TypeConverters específicos de la aplicación
-keep class com.rayoai.data.local.db.Converters { *; }

# Mantener los miembros de clases con anotaciones de Room
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Mantener los campos anotados por Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Mantener todas las clases de Gson y sus miembros necesarios para la serialización/deserialización
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Mantener los constructores de las clases de datos para Gson
-keepclassmembers class com.rayoai.data.local.model.CaptureEntity {
    <init>(...);
}
-keepclassmembers class com.rayoai.domain.model.ChatMessage {
    <init>(...);
}

# Mantener el uso de genéricos (para que Gson pueda deserializar listas)
-keepattributes Signature

# Mantener las anotaciones de Kotlin para que la reflexión funcione correctamente
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-keepattributes AnnotationDefault,Annotation,Deprecated,EnclosingMethod,Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleAnnotations,RuntimeInvisibleParameterAnnotations

# Mantener los enums si se usan en la serialización/deserialización
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}