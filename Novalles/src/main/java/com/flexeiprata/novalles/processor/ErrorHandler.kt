package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.utils.writingtools.findAnnotation
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType

internal class ErrorHandler(private val logger: KSPLogger) {

    fun checkDataClassUIModel(declaration: KSClassDeclaration) {
        val parameters = declaration.primaryConstructor?.parameters.orEmpty()
        val notProperty = parameters.find { !it.isVal && !it.isVar }
        val hiddenProperty = parameters.find { parameter ->
            declaration.getDeclaredProperties().find { it.simpleName == parameter.name }?.let { it.isPrivate() || it.isProtected() } == true
        }

        val error = when {
            declaration.isAbstract() || declaration.isCompanionObject || declaration.primaryConstructor == null || declaration.classKind != ClassKind.CLASS -> Error.Critical(
                "UI model should be kotlin data class."
            )
            declaration.typeParameters.isNotEmpty() -> Error.Critical(
                "Generic UI models are not supported."
            )
            parameters.isEmpty() -> Error.Critical(
                "UI model should have at least one constructor property."
            )
            !declaration.isPublic() || declaration.primaryConstructor?.isPublic().not() || declaration.isInternal() -> Error.Critical(
                "This class and it's constructor should be public."
            )
            parameters.find { it.isVararg || !it.type.resolve().declaration.isPublic() } != null -> Error.Critical(
                "Some of class' parameters are not compatible with Novalles or are not public."
            )
            else -> Error.Clear
        }

        log(declaration, error)

        val parameterError = when {
            notProperty != null -> Error.Critical(
                "Constructor parameter ${notProperty.name?.asString()} should be declared as val or var, so Novalles can compare it."
            )
            hiddenProperty != null -> Error.Critical(
                "Property ${hiddenProperty.name?.asString()} should not be private or protected, so Novalles can compare it."
            )
            else -> Error.Clear
        }

        log(notProperty ?: hiddenProperty ?: declaration, parameterError)

    }

    fun checkInspector(declaration: KSClassDeclaration) {
        val error = when {
            declaration.isAbstract() || !declaration.isPublic() || declaration.isInternal() -> Error.Critical(
                "This instructor is not valid."
            )
            doesNotHaveValidHolder(declaration) -> Error.Critical(
                "Provided ViewHolder should be available for UI interfaces"
            )
            else -> Error.Clear
        }

        //Avoid to check if [Instructor] is super class to reduce generating time.
        log(declaration, error)
    }

    fun throwUnexpectedError(from: KSClassDeclaration): Nothing {
        log(from, Error.Critical("Unexpected error! Have you configured your UI Model properly?"))
        throw Exception("Unexpected error! Have you configured your UI Model properly?")
    }

    fun logError(node: KSNode, message: String): Nothing {
        log(node, Error.Critical(message))
        throw Exception(message)
    }

    private fun log(node: KSNode, vararg errors: Error) {
        errors.forEach {
            when (it) {
                Error.Clear -> Unit
                is Error.Critical -> {
                    logger.error(it.message, node)
                    throw it.throwIt()
                }
            }
        }
    }

    private fun doesNotHaveValidHolder(declaration: KSClassDeclaration): Boolean {
        return (declaration.findAnnotation(BindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.let {
            !it.isPublic() || it.isInternal()
        } ?: false
    }

    private sealed class Error {
        data class Critical(val message: String) : Error() {
            fun throwIt() = RuntimeException(message)
        }

        object Clear : Error()
    }

    fun Boolean?.not() = this == false

}
