package com.flexeiprata.novalles.annotations

import com.flexeiprata.novalles.interfaces.Novalles
import kotlin.reflect.KClass

/**
 * You should create a single catalogue class, f.e. an Object that has this annotation to enable catalogue feature.
 * This class should be created in the main UI module, where you want to use Novalles features. All other modules AND
 * the main module should have at least one [NovallesPage] class.
 * You should pass all [NovallesPage] classes to this annotation in [pages] parameter.
 * You should KEEP name of this class.
 *
 * @see [Novalles.provideUiInterfaceForAsFromCatalogue]
 * @see [Novalles.provideInspectorFromInstructorCatalogue]
 * @see [Novalles.provideInspectorFromModelCatalogue]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NovallesCatalogue(
    val pages: Array<KClass<*>>
)

/**
 * You should create a page class in each module that has [UIModel] classes, f.e. an Object that has this annotation to enable catalogue feature.
 * After that, you should pass name of these classes into the [NovallesCatalogue] annotation
 * You should KEEP name of this classes.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NovallesPage