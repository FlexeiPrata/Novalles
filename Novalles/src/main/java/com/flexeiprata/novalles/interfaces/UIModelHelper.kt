package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.annotations.*

/**
 * Call [areContentsTheSame] and [areItemsTheSame] in corresponding Diff Util's functions. Call either [changePayloads] or [changePayloadsMap] in the changePayloads function.
 *
 * @see [PrimaryTag]
 * @see [Decompose]
 * @see [NonUIProperty]
 */
interface UIModelHelper <in T> {

    /**
     * Check if [oldItem] and [newItem] are the same with [PrimaryTag] or with the first value in your [UIModel]
     */
    fun areItemsTheSame(oldItem: T, newItem: Any): Boolean

    /**
     * Check if contents of [oldItem] and [newItem] are the same. It ignores [NonUIProperty] and [PrimaryTag] values.
     */
    fun areContentsTheSame(oldItem: T, newItem: Any): Boolean

    /**
     * Returns a list with [BasePayload] from comparison of [oldItem] with [newItem].
     *
     * @see [changePayloadsMap]
     */
    fun changePayloads(oldItem: T, newItem: Any): List<BasePayload>

    /**
     * Returns a list with [R] from comparison of [oldItem] with [newItem]. Use it when you have already your own payloads interface. Be sure it implements [BasePayload].
     *
     * @see [changePayloads]
     */
    fun <R: BasePayload> changePayloadsMap(oldItem: T, newItem: Any): List<R>

}