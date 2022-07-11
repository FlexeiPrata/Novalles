package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSReferenceElement

fun KSReferenceElement?.isPrimitive(): Boolean {
    return this?.toString() in listOf("String", "Int", "Short", "Number", "Boolean", "Byte", "Char", "Float", "Double", "Long", "Unit", "Any")
}

enum class KUIAnnotations {
    UIModel, PrimaryTag, NonUIProperty, Decompose
}

fun KUIAnnotations.toName(): String {
    return this.name
}