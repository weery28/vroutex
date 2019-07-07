package me.coweery.vroutex.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class QueryParam(
    val name: String
)