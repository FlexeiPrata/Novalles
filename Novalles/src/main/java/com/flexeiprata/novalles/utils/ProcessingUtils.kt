package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSReferenceElement
import com.google.devtools.ksp.symbol.KSTypeReference

internal fun KSReferenceElement?.isPrimitive(): Boolean {
    return this?.toString() in listOf("String", "Int", "Short", "Number", "Boolean", "Byte", "Char", "Float", "Double", "Long", "Unit", "Any")
}

internal fun KSTypeReference?.isPrimitive(): Boolean {
    return this?.toString() in listOf("String", "Int", "Short", "Number", "Boolean", "Byte", "Char", "Float", "Double", "Long", "Unit", "Any")
}

internal enum class KUIAnnotations {
    UIModel, PrimaryTag, NonUIProperty, Decompose
}

internal fun KUIAnnotations.toName(): String {
    return this.name
}