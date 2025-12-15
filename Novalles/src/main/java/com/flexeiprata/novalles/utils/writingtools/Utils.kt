package com.flexeiprata.novalles.utils.writingtools

import com.flexeiprata.novalles.utils.DecomposedEncapsulation
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import kotlin.reflect.KClass

internal fun KSValueParameter.writeAsVariable(parent: DecomposedEncapsulation? = null): String {
    val resolved = type.resolve()
    val name = resolved.declaration.simpleName.getShortName()
    val args = type.element?.typeArguments?.toTypeString().orEmpty()
    val nullable = if (resolved.isMarkedNullable || parent?.nullable == true) "?" else ""
    return "$name$args$nullable"
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
        else -> {
            joinToString(
                prefix = "<",
                postfix = ">",
                separator = ", "
            ) {
                val resolved = it.type!!.resolve()
                resolved.declaration.simpleName.getShortName() + if (resolved.isMarkedNullable) "?" else ""
            }
        }
    }
}

