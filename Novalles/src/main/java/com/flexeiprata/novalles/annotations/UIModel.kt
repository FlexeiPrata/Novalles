package com.flexeiprata.novalles.annotations

import com.flexeiprata.novalles.interfaces.*

/**
 * Class, annotated with it is considered to be a UIModel and can be used with [Novalles] in your recycler adapter.
 * Use [PrimaryTag], [NonUIProperty] and [Decompose] annotations to configure behavior of payload generating.
 * Note: if there is no [PrimaryTag] annotation, for [UIModelHelper.areItemsTheSame] first value of this class will be used.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UIModel

/**
 * First value annotated with it will be used in comparison inside [UIModelHelper.areItemsTheSame].
 * This value will be also ignored during [UIModelHelper.areContentsTheSame].
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PrimaryTag

/**
 * This value will not be used during [UIModelHelper.areContentsTheSame] comparison.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class NonUIProperty

/**
 * Value, annotated with it will be decomposed with its own values.
 * For example, if your field have 3 values, they will be used in any Novalles' actions separately:
 * Novalles will generate 3 different payloads in [UIModelHelper.changePayloads], compare them in [UIModelHelper.areContentsTheSame] separately.
 * Also, if you use [AutoBindViewHolder], you should use set${FieldName}In${DecomposedFieldName}() functions in your viewHolder for each field of your decomposed value.
 *
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Decompose