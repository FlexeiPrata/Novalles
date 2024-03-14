package com.flexeiprata.novalles.annotations

import com.flexeiprata.novalles.interfaces.Novalles

/**
 * You should create a single catalogue class, f.e. an Object that has this annotation to enable catalogue feature.
 * You should KEEP name of this class
 *
 * @see [Novalles.provideUiInterfaceForAsFromCatalogue]
 * @see [Novalles.provideInspectorFromInstructorCatalogue]
 * @see [Novalles.provideInspectorFromModelCatalogue]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NovallesCatalogue
