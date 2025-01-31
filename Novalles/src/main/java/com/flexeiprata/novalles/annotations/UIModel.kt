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

/**
 * Value, annotated with it will be decomposed with its own values.
 * For example, if your field have 3 values, they will be used in any Novalles' actions separately:
 * Novalles will generate 3 different payloads in [UIModelHelper.changePayloads], compare them in [UIModelHelper.areContentsTheSame] separately.
 * In the bound with [BindViewHolder] ViewHolder, you should use ${prefix}${FieldName}In${DecomposedFieldName}${postfix}() functions in your viewHolder for each field of your decomposed value.
 *
 * This annotation may cause errors, so be aware to handle it properly.
 */
@Deprecated("This annotation is now obsolete and soon will be removed from the Novalles library. Consider using embedded values as separated field in your UI model.")
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Decompose