package com.flexeiprata.novalles.interfaces

import kotlin.reflect.KClass

interface Catalogue {

    fun <T> provideUiModel(clazz: KClass<*>): UIModelHelper<T>?

    fun <T : Instructor> provideInspector(clazz: KClass<*>): Inspector<T, Any, Any>?

}

interface CataloguePage {

    fun eligible(clazz: KClass<*>): Boolean

    fun <T> provideUiModel(clazz: KClass<*>): UIModelHelper<T>?

    fun <T : Instructor> provideInspector(clazz: KClass<*>): Inspector<T, Any, Any>?

}