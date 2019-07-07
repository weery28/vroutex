package me.coweery.vroutex

import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import me.coweery.vroutex.annotations.Body
import me.coweery.vroutex.annotations.Header
import me.coweery.vroutex.annotations.PathParam
import me.coweery.vroutex.annotations.QueryParam
import me.coweery.vroutex.annotations.RequestMethod
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType

class Registrator(
    private val router: Router
) {
    private var globalExceptionHandler: ((Throwable, RoutingContext) -> Unit)? = null
    private var argumentsExceptionHandler: ((Throwable, RoutingContext) -> Unit)? = null
    private val globalGetters: MutableMap<Class<*>, (RoutingContext) -> Any> = mutableMapOf()

    fun setExceptionHandler(handler: ((Throwable, RoutingContext) -> Unit)): Registrator {
        globalExceptionHandler = handler
        return this
    }

    fun setArgumentExceptionHandler(handler: ((Throwable, RoutingContext) -> Unit)): Registrator {
        argumentsExceptionHandler = handler
        return this
    }

    fun <T : Any> addGetter(c: Class<T>, getter: (RoutingContext) -> T): Registrator {
        globalGetters[c] = getter
        return this
    }

    fun register(controller: Any, getters: Map<Class<*>, (RoutingContext) -> Any> = mapOf()) {

        controller::class.java.declaredMethods
            .filter {
                it.getAnnotation(RequestMethod::class.java) != null
            }
            .forEach {
                it.isAccessible = true
                registerMethod(it, controller, getters)
            }
    }

    private fun registerMethod(method: Method, controller: Any, getters: Map<Class<*>, (RoutingContext) -> Any>) {

        val annotation = method.getAnnotation(RequestMethod::class.java)
        val argsBuilder = getArgsBuilder(method, controller, getters)
        router.route(annotation.method, annotation.path).handler { routingContext ->
            try {
                when (method.returnType) {
                    Single::class.java -> {
                        (method.invoke(controller, *argsBuilder(routingContext)) as Single<*>).subscribe(
                            { res ->
                                routingContext.response().end(Json.encode(res))
                            },
                            {
                                catchException(routingContext, it)
                            }
                        )
                    }
                    Completable::class.java -> {
                        (method.invoke(controller, *argsBuilder(routingContext)) as Completable).subscribe(
                            {
                                routingContext.response().end()
                            },
                            {
                                catchException(routingContext, it)
                            }
                        )
                    }
                    else -> {
                        try {
                            method.invoke(controller, *argsBuilder(routingContext))
                        } catch (e: Exception) {
                            catchException(routingContext, e)
                        }
                    }
                }
            } catch (e: Exception) {
                catchException(routingContext, e)
            }
        }
    }

    private fun catchException(routingContext: RoutingContext, e: Throwable) {

        val t = if (e is InvocationTargetException) {
            e.targetException
        } else {
            e
        }

        if (t is ArgumentsException) {
            if (argumentsExceptionHandler != null) {
                argumentsExceptionHandler?.invoke(e, routingContext)
            } else {
                routingContext.response().statusCode = 400
                routingContext.response().end()
            }
        } else {
            if (globalExceptionHandler != null) {
                globalExceptionHandler?.invoke(t, routingContext)
            } else {
                t.printStackTrace()
                routingContext.response().statusCode = 500
                routingContext.response().end()
            }
        }
    }

    private fun getArgsBuilder(method: Method, controller: Any, getters: Map<Class<*>, (RoutingContext) -> Any>):
        (RoutingContext) -> Array<Any> {

        val res = mutableListOf<(RoutingContext) -> Any>()

        method.parameters.forEachIndexed { i, param ->
            param.getAnnotation(QueryParam::class.java)?.let { queryParamAnnotation ->
                val parser = createUrlParamsParser(param)
                res.add {
                    return@add parser(it.queryParam(queryParamAnnotation.name))
                }
                return@forEachIndexed
            }
            param.getAnnotation(Body::class.java)?.let {
                res.add {
                    return@add try {
                        it.bodyAsJson.mapTo(param.type)
                    } catch (e: Exception) {
                        throw ArgumentsException(e.message)
                    }
                }
                return@forEachIndexed
            }
            param.getAnnotation(PathParam::class.java)?.let { pathParamAnnotation ->
                PathParam.checkRequirements(method, pathParamAnnotation.name, controller)
                val parser = createUrlParamParser(param)
                res.add {
                    return@add parser(it.pathParam(pathParamAnnotation.name))
                }
                return@forEachIndexed
            }
            param.getAnnotation(Header::class.java)?.let { headerAnnotation ->
                res.add {
                    return@add it.request().getHeader(headerAnnotation.name)
                }
                return@forEachIndexed
            }

            if (param.type == RoutingContext::class.java) {
                res.add { return@add it }
                return@forEachIndexed
            }

            if (globalGetters.containsKey(param.type)) {
                res.add(globalGetters[param.type]!!)
                return@forEachIndexed
            }

            if (getters.containsKey(param.type)) {
                res.add(getters[param.type]!!)
                return@forEachIndexed
            }

            throw IllegalArgumentException(
                " -- ${controller.javaClass.name} -- " +
                    "Expected declaration for argument injection: ${param.name} : ${param.type}"
            )
        }

        return { routingContext ->
            res.map { it(routingContext) }.toTypedArray()
        }
    }

    private fun createUrlParamParser(param: Parameter): (String) -> Any {
        return when (param.type) {
            String::class.java -> { value ->
                value
            }
            Int::class.java -> { value ->
                value.toInt()
            }
            Long::class.java -> { value ->
                value.toLong()
            }
            Float::class.java -> { value ->
                value.toFloat()
            }
            else -> throw ArgumentsException()
        }
    }

    private fun createUrlParamsParser(param: Parameter): (List<String>) -> Any {
        return when (param.type) {
            String::class.java -> { value ->
                value.first()
            }
            Int::class.java -> { value ->
                value.first().toInt()
            }
            Long::class.java -> { value ->
                value.first().toLong()
            }
            Float::class.java -> { value ->
                value.first().toFloat()
            }
            List::class.java -> {
                val className = (param.parameterizedType as ParameterizedType).actualTypeArguments.first().typeName
                val genericClass = Class.forName(className).kotlin
                return when (genericClass) {
                    String::class -> { value ->
                        value
                    }
                    Int::class -> { value ->
                        value.map { it.toInt() }
                    }
                    Long::class -> { value ->
                        value.map { it.toLong() }
                    }
                    Float::class -> { value ->
                        value.map { it.toFloat() }
                    }
                    else -> throw ArgumentsException()
                }
            }
            else -> throw ArgumentsException()
        }
    }

}