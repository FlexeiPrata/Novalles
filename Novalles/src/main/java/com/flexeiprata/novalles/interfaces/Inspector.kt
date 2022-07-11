package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.annotations.*

/**
 * Base interface to group up Instructor classes
 */
interface Instructor

/**
 * Returned by [Novalles.provideInspectorFromInstructor]. Use it in onBindViewHolder function with Payloads list.
 * Be sure, that you get payloads list properly to avoid unexpected behaviour.
 *
 * @see [Instructor]
 * @see [Instruction]
 * @see [AutoBindViewHolder]
 */
interface Inspector {

    /**
     * For each payload in [payloads] list call a viewHolder corresponding function.
     * The corresponding function is chosen in 2 ways:
     * 1. You annotated one of [Instructor] function with [BindOn] annotation, where [BindOn.on] is string value of the corresponding UI model field.
     * 2. You annotated your [Instructor] with [AutoBindViewHolder], where corresponding function to each field is set${fieldName}() function in [AutoBindViewHolder.viewHolder]. Functions should be public with only one argument.
     * Note: when you cannot use [AutoBindViewHolder] functionality, you should rely on [Instructor] functions.
     *
     * @see [AutoBindViewHolder]
     * @see [Novalles.provideInspectorFromInstructor]
     * @see [Decompose]
     */
    fun inspectPayloads(payloads: List<Any>)
}