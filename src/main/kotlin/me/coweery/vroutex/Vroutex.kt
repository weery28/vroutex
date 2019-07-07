package me.coweery.vroutex

import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.BodyHandler
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Controller



class Vroutex private constructor() {

    companion object {

        fun run(verticle: AbstractVerticle, port: Int) {

            val server = HttpServer(verticle.vertx.createHttpServer())
            val router = createRouter(verticle)
            val context = SpringApplication.run(verticle::class.java)
            val registrator = createRegistrator(router, context)

            init(context, registrator)
            server.requestHandler(router)
            server.listen(port)
        }

        private fun init(applicationContext: ApplicationContext, registrator: Registrator) {

            applicationContext.getBeanNamesForAnnotation(Controller::class.java).forEach {

                val controller = applicationContext.getBean(it)
                registrator.register(controller)
            }
        }

        private fun createRouter(verticle: AbstractVerticle): Router {
            val classicRouter = io.vertx.ext.web.Router.router(verticle.vertx)
            val router = Router(classicRouter)
            router.route().handler(BodyHandler.create())

            return router
        }

        private fun createRegistrator(router: Router, context: ApplicationContext): Registrator {
            val registrator = Registrator(router)

            try {
                context.getBean(VroutexConfigurator::class.java)
                    .configure(registrator)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return registrator
        }
    }
}