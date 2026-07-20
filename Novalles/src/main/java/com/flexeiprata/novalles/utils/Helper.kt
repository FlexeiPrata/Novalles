package com.flexeiprata.novalles.utils

internal data class Payloading(
    val name: String,
    val isNullable: Boolean
)

internal data class CachedField(
    val name: String,
    val isNullable: Boolean
)

internal data class InspectorFunData(
    val name: String,
    val arg: String,
    val isNullable: Boolean?,
    val isBoolean: Boolean? = null
)

internal data class InspectorFunDataFlat(
    val name: String,
    val target: String
)
