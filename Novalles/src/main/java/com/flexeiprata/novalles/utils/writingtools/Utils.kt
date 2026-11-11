package com.flexeiprata.novalles.utils.writingtools

import com.google.devtools.ksp.symbol.*
import kotlin.reflect.KClass

internal fun KSValueParameter.writeAsVariable(): String {
    return type.resolve().toQualifiedString()
}

//Fully qualified and recursive, so nested generics compile and same-named types from different packages never clash.
internal fun KSType.toQualifiedString(): String {
    val name = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val nullable = if (isMarkedNullable) "?" else ""
    return "$name${arguments.toTypeString()}$nullable"
}

/**
 * Indexes [this] by [keyOf], keeping the FIRST element on duplicate keys.
 * This mirrors `find { }`, which associateBy does not — it keeps the last.
 */
internal inline fun <T, K> Iterable<T>.firstWinsBy(keyOf: (T) -> K): Map<K, T> {
    val result = LinkedHashMap<K, T>()
    for (element in this) result.putIfAbsent(keyOf(element), element)
    return result
}

internal fun KSDeclaration.findAnnotation(annotation: KClass<*>): KSAnnotation? {
    return annotations.find { it.shortName.getShortName() == annotation.simpleName }
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
            ) { argument ->
                //A star projection has no type, f.e. List<*>.
                val resolved = argument.type?.resolve() ?: return@joinToString "*"
                when (argument.variance) {
                    Variance.STAR -> "*"
                    Variance.COVARIANT -> "out ${resolved.toQualifiedString()}"
                    Variance.CONTRAVARIANT -> "in ${resolved.toQualifiedString()}"
                    Variance.INVARIANT -> resolved.toQualifiedString()
                }
            }
        }
    }
}

