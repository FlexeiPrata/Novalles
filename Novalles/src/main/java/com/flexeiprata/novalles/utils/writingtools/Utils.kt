package com.flexeiprata.novalles.utils.writingtools

import com.google.devtools.ksp.symbol.*
import kotlin.reflect.KClass

internal fun KSValueParameter.writeAsVariable(): String {
    val resolved = type.resolve()
    val name = resolved.declaration.simpleName.getShortName()
    val args = type.element?.typeArguments?.toTypeString().orEmpty()
    val nullable = if (resolved.isMarkedNullable) "?" else ""
    return "$name$args$nullable"
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
                resolved.declaration.simpleName.getShortName() + if (resolved.isMarkedNullable) "?" else ""
            }
        }
    }
}

