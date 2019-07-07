package me.coweery.vroutex

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Controller

class Vroutex constructor(
    private val applicationContext: ApplicationContext,
    private val registrator: Registrator
) {

    init {
        applicationContext.getBeanNamesForAnnotation(Controller::class.java).forEach {

            val controller = applicationContext.getBean(it)
            registrator.register(controller)
        }
    }
}