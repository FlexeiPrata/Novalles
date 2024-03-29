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
 * @see [BindViewHolder]
 */
interface Inspector <I: Instructor, V: Any, M: Any> {

    /**
     * For each payload in [payloads] list call a viewHolder corresponding function.
     *
     * You can pass as [payloads]:
     * 1. List<Any>?
     * 2. List<List<Any>>?
     * 3. Any tag, you've registered with [BindOnTag].
     * 4. Default Any? from onBindViewHolder.
     * The corresponding function is chosen in 2 ways:
     * 1. You annotated one of [Instructor] function with [BindOn] annotation, where [BindOn.on] is string value of the corresponding UI model field.
     * 2. You annotated your [Instructor] with [BindViewHolder], where corresponding function to each field is set${fieldName}() function in [BindViewHolder.viewHolder]. Functions should be public with only one argument.
     * Note: when you cannot use [BindViewHolder] functionality, you should rely on [Instructor] functions.
     *
     * @see [BindViewHolder]
     * @see [Novalles.provideInspectorFromInstructor]
     * @see [Decompose]
     */
    fun inspectPayloads(payloads: Any?, instructor: I, viewHolder: V? = null, doOnBind: () -> Unit)

    /**
     * This functions will call all viewHolder's and [Instructor]'s functions that are bound to fields. It can be used to set initial UI for your viewHolder.
     *
     * @see [BindViewHolder]
     * @see [Instructor]
     * @see [BindOn]
     */
    fun bind(model: M, viewHolder: V, instructor: I)
}