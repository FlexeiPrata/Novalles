package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

data class DecomposedEncapsulation(
    val clazz: KSClassDeclaration,
    val fieldName: String,
    val params: List<KSValueParameter>,
    val nullable: Boolean
) {
    val dot get() = if (nullable) "?." else "."
}

data class Payloading(
    val name: String,
    val isNullable: Boolean
)

data class CachedField(
    val name: String,
    val isNullable: Boolean,
    val variableName: String? = null,
)

data class InspectorFunData(
    val name: String,
    val arg: String,
    val isNullable: Boolean?,
    val isBoolean: Boolean? = null
)

data class InspectorFunDataFlat(
    val name: String,
    val target: String
)