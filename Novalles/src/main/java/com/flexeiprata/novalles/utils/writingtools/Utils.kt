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
    return "${this.type.element}${this.type.element?.typeArguments?.toTypeString()}" +
            if (this.type.resolve().isMarkedNullable || parent?.nullable == true) "?" else ""
}

inline fun <T> Sequence<T>.findOut(predicate: (T) -> Boolean): Boolean {
    return this.find(predicate) != null
}

fun KSDeclaration.findAnnotation(annotation: KClass<*>): KSAnnotation? {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName }
}

fun KSDeclaration.hasAnnotation(annotation: KClass<*>): Boolean {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName } != null
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

/**
 * @throws TypeCastException
 */
inline fun <reified T> List<KSValueArgument>.retrieveArg(name: String): T {
    return find { it.name?.getShortName() == name }?.value as T
}

fun List<KSTypeArgument>.toTypeString(): String {
    return when {
        isNullOrEmpty() -> ""
        else -> joinToString(
            prefix = "<",
            postfix = ">",
            separator = ", "
        ) { it.type.toString() + if (it.type?.resolve()?.isMarkedNullable == true) "?" else "" }
    }
}

