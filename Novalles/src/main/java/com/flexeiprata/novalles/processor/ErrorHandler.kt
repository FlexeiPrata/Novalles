package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.AutoBindViewHolder
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.utils.KUIAnnotations
import com.flexeiprata.novalles.utils.isPrimitive
import com.flexeiprata.novalles.utils.writingtools.findAnnotation
import com.flexeiprata.novalles.utils.writingtools.findOut
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*

class ErrorHandler(private val logger: KSPLogger) {

    fun checkDataClassUIModel(declaration: KSClassDeclaration) {
        val error = when {
            declaration.isAbstract() || declaration.isCompanionObject || declaration.primaryConstructor == null || declaration.classKind != ClassKind.CLASS -> Error.Critical(
                "UI model should be kotlin data class."
            )
            !declaration.isPublic() || declaration.primaryConstructor?.isPublic().not() || declaration.isInternal() -> Error.Critical(
                "This class and it's constructor should be public."
            )
            declaration.primaryConstructor?.parameters?.find { it.isVararg || !it.type.resolve().declaration.isPublic() } != null -> Error.Critical(
                "Some of class' parameters are not compatible with Novalles or are not public."
            )
            hasDecomposedPrimitives(declaration) -> Error.Critical(
                "Decomposed value should not be primitive."
            )
            else -> Error.Clear
        }

        log(declaration, error, Error.Warnings(emptyList()))

    }

    fun checkInspector(declaration: KSClassDeclaration) {
        val error = when {
            declaration.isAbstract() || !declaration.isPublic() || declaration.isInternal() -> Error.Critical(
                "This instructor is not valid."
            )
            doesNotHaveValidHolder(declaration) || doesNotHaveValidHolderDeprecated(declaration) -> Error.Critical(
                "Provided ViewHolder should be available for UI interfaces"
            )
            else -> Error.Clear
        }

        //Avoid to check if [Instructor] is super class to reduce generating time.
        val warnings = Error.Clear

        log(declaration, error, warnings)
    }

    fun checkDecomposedValue(node: KSClassDeclaration, decompose: KSValueParameter) {
        val clazz = decompose.type.resolve().declaration.closestClassDeclaration()
        val error = when {
            clazz == null -> Error.Critical(
                "Unexpected error during finding class declaration"
            )
            !clazz.isPublic() || clazz.isInternal() -> Error.Critical(
                "The decomposed value is not valid."
            )
            clazz.getAllProperties().findOut { !it.isPublic() } -> Error.Critical(
                "The decomposed value should be data class with public fields"
            )
            decompose.type.element.isPrimitive() -> Error.Critical(
                "Decomposed value should not be primitive"
            )
            else -> Error.Clear
        }

        val warnings = Error.Clear

        log(node, error, warnings)
    }

    fun throwUnexpectedError(from: KSClassDeclaration): Nothing {
        log(from, Error.Critical("Unexpected error! Have you configured your UI Model properly?"))
        throw Exception("Unexpected error! Have you configured your UI Model properly?")
    }

    fun logError(node: KSClassDeclaration, message: String): Nothing {
        log(node, Error.Critical(message))
        throw Exception(message)
    }

    private fun log(node: KSClassDeclaration, vararg errors: Error) {
        errors.forEach {
            when (it) {
                Error.Clear -> Unit
                is Error.Critical -> {
                    logger.error(it.message, node)
                    throw it.throwIt()
                }
                is Error.Warnings -> {
                    it.messages.forEach { message ->
                        logger.warn(message, node)
                    }
                }
            }
        }
    }

    private fun doesNotHaveValidHolder(declaration: KSClassDeclaration): Boolean {
        return (declaration.findAnnotation(BindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.let {
            !it.isPublic() || it.isInternal()
        } ?: false
    }

    @Suppress("DEPRECATION")
    private fun doesNotHaveValidHolderDeprecated(declaration: KSClassDeclaration): Boolean {
        return (declaration.findAnnotation(AutoBindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.let {
            !it.isPublic() || it.isInternal()
        } ?: false
    }

    //Now deprecated
    /*private fun allBindOnFunctionAreSingleArgument(declaration: KSClassDeclaration): Boolean {
        return declaration.getAllFunctions().filter { it.annotations.find { it.shortName.asString() == BindOn::class.simpleName } != null }.find {
            it.parameters.isNotEmpty() || !it.isPublic()
        } != null
    }*/

    private fun hasDecomposedPrimitives(declaration: KSClassDeclaration): Boolean {
        return declaration.primaryConstructor?.parameters?.find { parameter ->
            parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.Decompose.name } != null && parameter.type.isPrimitive()
        } != null
    }


    private sealed class Error {
        data class Warnings(val messages: List<String>) : Error()
        data class Critical(val message: String) : Error() {
            fun throwIt() = RuntimeException(message)
        }

        object Clear : Error()
    }

    fun Boolean?.not() = this == false
    fun Boolean?.assure() = this == true

}