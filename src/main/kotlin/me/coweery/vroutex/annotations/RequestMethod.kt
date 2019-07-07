package me.coweery.vroutex.annotations

import io.vertx.core.http.HttpMethod

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RequestMethod(
    val path: String,
    val method: HttpMethod
)