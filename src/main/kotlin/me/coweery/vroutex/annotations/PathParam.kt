package me.coweery.vroutex.annotations

import java.lang.reflect.Method

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class PathParam(
    val name: String
) {
    companion object {

        fun checkRequirements(method: Method, paramName: String, controller: Any) {

            if (method.getAnnotation(RequestMethod::class.java)?.path?.contains(":$paramName") != true) {
                throw IllegalArgumentException("Path does not contains $paramName path param for method ${method.name}" +
                    "in ${controller.javaClass.name}")
            }
        }
    }
}