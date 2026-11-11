package androidx.annotation

@Retention(AnnotationRetention.SOURCE)
annotation class OptIn(vararg val markerClass: kotlin.reflect.KClass<out Annotation>)
