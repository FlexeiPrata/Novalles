package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSReferenceElement
import com.google.devtools.ksp.symbol.KSTypeReference

private val PRIMITIVES = hashSetOf(
    "String", "Int", "Short", "Number", "Boolean", "Byte", "Char", "Float", "Double", "Long",
    "Unit", "Any"
)

internal fun KSReferenceElement?.isPrimitive(): Boolean {
    return this != null && toString() in PRIMITIVES
}

internal fun KSTypeReference?.isPrimitive(): Boolean {
    return this != null && toString() in PRIMITIVES
}

internal enum class KUIAnnotations {
    UIModel, PrimaryTag, NonUIProperty
}
