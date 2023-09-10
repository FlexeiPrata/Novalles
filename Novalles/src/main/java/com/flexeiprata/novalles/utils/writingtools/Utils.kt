package com.flexeiprata.novalles.utils.writingtools

import com.flexeiprata.novalles.utils.DecomposedEncapsulation
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import kotlin.reflect.KClass

internal fun KSValueParameter.writeAsVariable(parent: DecomposedEncapsulation? = null): String {
    return "${this.type.element}${this.type.element?.typeArguments?.toTypeString()}" +
            if (this.type.resolve().isMarkedNullable || parent?.nullable == true) "?" else ""
}

internal inline fun <T> Sequence<T>.findOut(predicate: (T) -> Boolean): Boolean {
    return this.find(predicate) != null
}

internal fun KSDeclaration.findAnnotation(annotation: KClass<*>): KSAnnotation? {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName }
}

internal fun KSDeclaration.hasAnnotation(annotation: KClass<*>): Boolean {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName } != null
}

internal fun String.appendIf(condition: Boolean, text: String): String {
    return if (condition) "$this$text" else this
}

internal fun KSFunctionDeclaration.isFirstArgNullable(): Boolean {
    return parameters.first().type.resolve().isMarkedNullable
}

internal inline fun <reified T> tryNull(action: () -> T): T? {
    return try {
        action()
    } catch (ex: Exception) {
        null
    }
}

/**
 * @throws TypeCastException
 */
internal inline fun <reified T> List<KSValueArgument>.retrieveArg(name: String): T {
    return find { it.name?.getShortName() == name }?.value as T
}

internal fun List<KSTypeArgument>.toTypeString(): String {
    return when {
        isNullOrEmpty() -> ""
        else -> joinToString(
            prefix = "<",
            postfix = ">",
            separator = ", "
        ) { it.type.toString() + if (it.type?.resolve()?.isMarkedNullable == true) "?" else "" }
    }
}

