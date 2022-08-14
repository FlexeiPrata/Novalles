package com.flexeiprata.novalles.annotations

import kotlin.reflect.KClass
import com.flexeiprata.novalles.interfaces.Instructor

/**
 * If you want to notify adapter manually with notify functions, you can pass to these functions instance of [tag] and
 * register it to your [Instructor] with this annotation.
 *
 * @see [Instruction]
 * @see [BindOn]
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class BindOnTag(val tag: KClass<*>)