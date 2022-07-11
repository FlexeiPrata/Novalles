package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.AutoBindViewHolder
import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.utils.KUIAnnotations
import com.flexeiprata.novalles.utils.writingtools.findAnnotation
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

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
            else -> Error.Clear
        }

        val warnings = Error.Warnings(
            mutableListOf<String>().apply {
                if (hasDecomposedFields(declaration)) add("This class has @Decompose experimental annotation. Please, report any happened issue.")
            }
        )

        log(declaration, error, warnings)

    }

    fun checkInspector(declaration: KSClassDeclaration) {
        val error = when {
            declaration.isAbstract() || !declaration.isPublic() || declaration.isInternal() -> Error.Critical(
                "This instructor is not valid."
            )
            allBindOnFunctionAreSingleArgument(declaration) -> Error.Critical(
                "All @BindOn function should have only one param and have public visibility"
            )
            doesNotHaveValidHolder(declaration) -> Error.Critical(
                "Provided ViewHolder should be available for UI interfaces"
            )
            else -> Error.Clear
        }

        //Avoid to check if [Instructor] is super class to reduce generating time.
        val warnings = Error.Clear

        log(declaration, error, warnings)
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
        return (declaration.findAnnotation(AutoBindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.let {
            !it.isPublic() || it.isInternal()
        } ?: false
    }

    private fun allBindOnFunctionAreSingleArgument(declaration: KSClassDeclaration): Boolean {
        return declaration.getAllFunctions().filter { it.annotations.find { it.shortName.asString() == BindOn::class.simpleName } != null }.find {
            it.parameters.size > 1 || !it.isPublic()
        } != null
    }

    private fun hasDecomposedFields(declaration: KSClassDeclaration): Boolean {
        return declaration.primaryConstructor?.parameters?.find { parameter ->
            parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.Decompose.name } != null
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