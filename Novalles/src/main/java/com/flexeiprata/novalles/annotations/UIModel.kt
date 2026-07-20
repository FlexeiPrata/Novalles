package com.flexeiprata.novalles.annotations

import com.flexeiprata.novalles.interfaces.*

/**
 * Class, annotated with it is considered to be a UIModel and can be used with [Novalles] in your recycler adapter.
 * Use [PrimaryTag] and [NonUIProperty] annotations to configure behavior of payload generating.
 * Note: if there is no [PrimaryTag] annotation, for [UIModelHelper.areItemsTheSame] first value of this class will be used.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UIModel

/**
 * First value annotated with it will be used in comparison inside [UIModelHelper.areItemsTheSame].
 * This value will be also ignored during [UIModelHelper.areContentsTheSame].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class PrimaryTag

/**
 * This value will not be used during [UIModelHelper.areContentsTheSame] comparison.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class NonUIProperty