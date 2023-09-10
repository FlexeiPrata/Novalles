package com.flexeiprata.novalles.annotations

import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.interfaces.Instructor
import kotlin.reflect.KClass

/**
 * Class annotated with it is considered to be the instruction how to handle payloads for [model] UI Model.
 * It should also implements [Instructor] interface.
 *
 * @see [Inspector]
 * @see [BindOn]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Instruction(val model: KClass<*>)

/**
 * This annotation should be used inside [Instruction]/[Instructor] class to show Novalles how to handle payload.
 * The annotated function should represent an action, that should be performed when [on] value of your UI Model has been changed.
 * Your function can have no arguments, but can use any external variables or objects.
 * Your function can have one boolean argument, which represents when function has been called (true from bind, false from payloads)
 * You can bind more than one field with [BindOnFields].
 *
 * @see [BindOnFields]
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class BindOn(val on: String)

/**
 * Use as the [BindOn] annotation, but can bind more than one field.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class BindOnFields(val on: Array<String>)

/**
 * This annotation helps your [Instructor] to handle payloads directly with viewHolder functions.
 * Your function from [viewHolder] will be automatically bind in [Inspector] if it corresponds the following pattern ${prefix}${FieldName}${postfix}(field: FieldType).
 * The default [prefix] is "set".
 * The default [bindPrefix] is "bind".
 * You'll get warnings during ksp generating, if there will be no corresponding functions in [Instructor] and your [viewHolder].
 *
 * @see [Decompose]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class BindViewHolder(val viewHolder: KClass<*>, val prefix: String = "set", val bindPrefix: String = "bind")

