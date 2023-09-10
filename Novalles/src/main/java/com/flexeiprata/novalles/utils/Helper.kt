package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

internal data class DecomposedEncapsulation(
    val clazz: KSClassDeclaration,
    val fieldName: String,
    val params: List<KSValueParameter>,
    val nullable: Boolean
) {
    val dot get() = if (nullable) "?." else "."
}

internal data class Payloading(
    val name: String,
    val isNullable: Boolean
)

internal data class CachedField(
    val name: String,
    val isNullable: Boolean,
    val variableName: String? = null,
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