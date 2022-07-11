package com.flexeiprata.novalles.utils.writingtools

import com.flexeiprata.novalles.utils.DecomposedEncapsulation
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import kotlin.reflect.KClass

fun <T> List<List<T>>.unwrap(): List<T> {
    val list = mutableListOf<T>()
    this.forEach {
        list.addAll(it)
    }
    return list
}


fun <T> Sequence<T>.validateIfRootInterface(): KSClassDeclaration? {
    return this.filter { it is KSClassDeclaration && it.validate() && it.classKind == ClassKind.INTERFACE }
        .firstOrNull()?.let {
            (it as KSClassDeclaration)
        }
}

fun KSValueParameter.writeAsVariable(parent: DecomposedEncapsulation? = null): String {
    return "${this.type.element}${if (this.type.resolve().isMarkedNullable || parent?.nullable == true) "?" else ""}"
}

inline fun <T> Sequence<T>.findOut(predicate: (T) -> Boolean): Boolean {
    return this.find(predicate) != null
}

fun KSDeclaration.findAnnotation(annotation: KClass<*>): KSAnnotation? {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName }
}

fun String.appendIf(condition: Boolean, text: String): String {
    return if (condition) "$this$text" else this
}

fun KSFunctionDeclaration.isFirstArgNullable(): Boolean {
    return parameters.first().type.resolve().isMarkedNullable
}

inline fun <reified T> tryNull(action: () -> T): T? {
    return try {
        action()
    } catch (ex: Exception) {
        null
    }
}

