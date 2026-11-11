package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.annotations.NovallesCatalogue
import com.flexeiprata.novalles.annotations.NovallesPage
import com.flexeiprata.novalles.annotations.UIModel
import kotlin.reflect.KClass

/**
 * Root registry of every [UIModelHelper] and [Inspector] in your app. Novalles generates its only implementation from your
 * [NovallesCatalogue] class, so you should not implement it yourself. Use it through [Novalles.provideUiInterfaceForAsFromCatalogue],
 * [Novalles.provideInspectorFromModelCatalogue] and [Novalles.provideInspectorFromInstructorCatalogue].
 *
 * @see [NovallesCatalogue]
 * @see [CataloguePage]
 */
interface Catalogue {

    /**
     * Returns the [UIModelHelper] of [clazz] UI Model, or null if no page knows it.
     */
    fun <T> provideUiModel(clazz: KClass<*>): UIModelHelper<T>?

    /**
     * Returns the [Inspector] of [clazz] UI Model, or null if no page knows it or the model has no [Instructor].
     */
    fun <T : Instructor> provideInspector(clazz: KClass<*>): Inspector<T, Any, Any>?

}

/**
 * Registry of the [UIModel] classes of one module. Novalles generates one page for each [NovallesPage] class,
 * so you should not implement it yourself. The [Catalogue] asks every page in turn.
 *
 * @see [NovallesPage]
 * @see [Catalogue]
 */
interface CataloguePage {

    /**
     * Returns true if [clazz] is a [UIModel] of this page's module.
     */
    fun eligible(clazz: KClass<*>): Boolean

    /**
     * Returns the [UIModelHelper] of [clazz] UI Model, or null if this page doesn't know it.
     */
    fun <T> provideUiModel(clazz: KClass<*>): UIModelHelper<T>?

    /**
     * Returns the [Inspector] of [clazz] UI Model, or null if this page doesn't know it or the model has no [Instructor].
     */
    fun <T : Instructor> provideInspector(clazz: KClass<*>): Inspector<T, Any, Any>?

}
