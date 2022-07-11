package com.flexeiprata.novalles.annotations

import kotlin.reflect.KClass
import com.flexeiprata.novalles.interfaces.*

/**
 * Class annotated with it is considered to be the instruction how to handle payloads for [model] UI Model.
 * It should also implements [Instructor] interface.
 *
 * @see [Inspector]
 * @see [BindOn]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Instruction(val model: KClass<*>)

/**
 * This annotation should be used inside [Instruction]/[Instructor] class to show Novalles how to handle payload.
 * The annotated function should represent an action, that should be performed when [on] value of your UI Model has been changed.
 * Your function should have only one argument of [on] value, but can use any external variables or objects.
 * You can automatize it, using [AutoBindViewHolder] annotation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class BindOn(val on: String)

/**
 * This annotation helps your [Instructor] to handle payloads directly with viewHolder functions.
 * Your function from [viewHolder] will be automatically bind in [Inspector] if it corresponds the following pattern set${FieldName}(field: FieldType).
 * You'll get warnings during ksp generating, if there will be no corresponding functions in [Instructor] and your [viewHolder].
 *
 * @see [Decompose]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class AutoBindViewHolder(val viewHolder: KClass<*>)

