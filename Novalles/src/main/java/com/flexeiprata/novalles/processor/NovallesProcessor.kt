package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.BindOnFields
import com.flexeiprata.novalles.annotations.BindOnTag
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.interfaces.UIModelHelper
import com.flexeiprata.novalles.utils.CachedField
import com.flexeiprata.novalles.utils.DecomposedEncapsulation
import com.flexeiprata.novalles.utils.InspectorFunData
import com.flexeiprata.novalles.utils.InspectorFunDataFlat
import com.flexeiprata.novalles.utils.KUIAnnotations
import com.flexeiprata.novalles.utils.Payloading
import com.flexeiprata.novalles.utils.isPrimitive
import com.flexeiprata.novalles.utils.writingtools.add
import com.flexeiprata.novalles.utils.writingtools.buildIn
import com.flexeiprata.novalles.utils.writingtools.capitalizeFirst
import com.flexeiprata.novalles.utils.writingtools.dataClassConstructor
import com.flexeiprata.novalles.utils.writingtools.findAnnotation
import com.flexeiprata.novalles.utils.writingtools.funHeaderBuilder
import com.flexeiprata.novalles.utils.writingtools.hasAnnotation
import com.flexeiprata.novalles.utils.writingtools.isFirstArgNullable
import com.flexeiprata.novalles.utils.writingtools.newLine
import com.flexeiprata.novalles.utils.writingtools.retrieveArg
import com.flexeiprata.novalles.utils.writingtools.writeAsVariable
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

class NovallesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val payloadsMap = mutableMapOf<String, MutableList<Payloading>>()
    private val cachedFieldsMap = mutableMapOf<String, MutableList<CachedField>>()
    private val catalogUIModels = mutableMapOf<String, String>()
    private val catalogInstruction = mutableMapOf<String, String>()
    private var catalogueCreated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(UIModel::class.qualifiedName!!)
        val instructors = resolver.getSymbolsWithAnnotation(Instruction::class.qualifiedName!!)

        val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())

        symbols.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(UIModelVisitor(dependencies), Unit) }

        instructors.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(ViewHoldersVisitor(dependencies), Unit) }

        //TODO: associate catalogue
        createCatalogue(dependencies)

        return symbols.plus(instructors).filterNot { it.validate() }.toList()
    }

    private fun createCatalogue(dependencies: Dependencies) {
        if (catalogueCreated) return

        //UI model catalogue
        val fileString = buildString {
            buildIn {
                appendIn("package ksp.novalles.catalogues")
                newLine()
                appendIn("import ${UIModelHelper::class.qualifiedName}")
                appendIn("import kotlin.reflect.KClass")
                appendIn("import com.flexeiprata.novalles.interfaces.Catalogue")
                appendIn("import androidx.annotation.Keep")
                appendIn("import com.flexeiprata.novalles.interfaces.Inspector")
                appendIn("import com.flexeiprata.novalles.interfaces.Instructor")
                newLine()
                appendIn("@Keep")
                appendIn("class NovallesCatalogue(): Catalogue { ")
                newLine()
                incrementLevel()
                appendIn(
                    funHeaderBuilder(
                        name = "provideUiModel",
                        genericString = "<T>",
                        args = listOf("classQualifiedName: String"),
                        returnType = "UIModelHelper<T>",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when (classQualifiedName) {")
                incrementLevel()
                catalogUIModels.keys.forEach { clazz ->
                    appendIn("\"$clazz\" -> ${catalogUIModels[clazz]}")
                }
                appendIn("else -> throw Exception(\"There is no UI interfaces. If it happened on the release build, check if you keep your UIModels' names.\")")
                appendDown("}")
                appendIn("return helper as UIModelHelper<T>")
                appendDown("}")
                newLine()
                appendIn(
                    funHeaderBuilder(
                        name = "provideInspector",
                        genericString = "<T: Instructor>",
                        args = listOf("classQualifiedName: String"),
                        returnType = "Inspector<T, Any, Any>",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when (classQualifiedName) {")
                incrementLevel()
                catalogUIModels.keys.forEach { clazz ->
                    appendIn("\"$clazz\" -> ${catalogInstruction[clazz]?.replace("PayloadOfUIModel", "Inspector")}()")
                }
                appendIn("else -> throw Exception(\"There is no UI Inspectors. If it happened on the release build, check if you keep your UIModels' names.\")")
                appendDown("}")
                appendIn("return helper as Inspector<T, Any, Any>")
                appendDown("}")
                closeFunctions()
            }
        }

        val file = codeGenerator.createNewFile(
            dependencies,
            "ksp.novalles.catalogues",
            "NovallesCatalogue"
        )

        file.write(fileString.toByteArray())
        catalogueCreated = true
    }

    private inner class ViewHoldersVisitor(val dependencies: Dependencies) : KSVisitorVoid() {

        val errorHandler = ErrorHandler(logger)


        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            fun throwUnexpected(): Nothing = errorHandler.throwUnexpectedError(classDeclaration)

            errorHandler.checkInspector(classDeclaration)

            val core = classDeclaration.findAnnotation(Instruction::class) ?: throwUnexpected()
            val model = (core.arguments.first().value as KSType).declaration.closestClassDeclaration() ?: throwUnexpected()
            val (viewHolder, prefix, bindPrefix) = extractViewHolderAnnotations(classDeclaration)
            val name = classDeclaration.simpleName.getShortName()

            val declarationFunctions = classDeclaration.getAllFunctions()

            val inspectorFunctions = declarationFunctions.filter {
                it.hasAnnotation(BindOn::class)
            }.map {
                InspectorFunData(
                    name = it.simpleName.getShortName(),
                    arg = it.annotations.first().arguments.first().value as String,
                    isNullable = it.parameters.firstOrNull()?.type?.resolve()?.isMarkedNullable,
                    isBoolean = it.parameters.firstOrNull()?.type?.toString() == "Boolean"
                )
            }.toList()

            val inspectorMultipleValues = declarationFunctions.filter { function ->
                function.hasAnnotation(BindOnFields::class)
            }.map { function ->
                val fields = function.findAnnotation(BindOnFields::class)?.arguments?.first()?.value as ArrayList<*>? ?: throwUnexpected()
                fields.filterIsInstance<String>().map {
                    InspectorFunDataFlat(
                        name = function.simpleName.getShortName(),
                        target = it
                    )
                }
            }.flatten()


            val tagFunctions = classDeclaration.getAllFunctions().filter {
                it.hasAnnotation(BindOnTag::class)
            }.map {
                it.simpleName.getShortName() to it.findAnnotation(BindOnTag::class)?.arguments?.first()?.value as KSType
            }

            val viewHolderFun =
                viewHolder.getAllFunctions().filterNot { !it.isPublic() }
            val payloads = payloadsMap[model.simpleName.getShortName()] ?: emptyList()
            val cachedFields = cachedFieldsMap[model.simpleName.getShortName()] ?: emptyList()

            //Imports
            val listOfImports = listOfNotNull(
                viewHolder.qualifiedName?.asString(),
                if (payloads.isNotEmpty()) "$PACKAGE.${model.simpleName.getShortName()}Payloads.*" else null,
                classDeclaration.qualifiedName?.asString(),
                Inspector::class.qualifiedName,
                model.qualifiedName?.asString(),
                "androidx.annotation.Keep",
                "com.flexeiprata.novalles.interfaces.Novalles"
            )

            val catalogueName = model.qualifiedName?.asString() ?: ""
            val catalogueValue = PACKAGE + ".${model.simpleName.getShortName()}PayloadOfUIModel"

            catalogInstruction[catalogueName] = catalogueValue
            val text = buildString {
                add("package $PACKAGE")
                listOfImports.forEach {
                    add("import $it")
                }
                newLine(2)

                buildIn {
                    appendIn("@Keep")
                    append("class ${model.simpleName.getShortName()}Inspector : Inspector<$name, ${viewHolder.simpleName.getShortName()}, ${model.simpleName.getShortName()}> {")
                    newLine(1)
                    appendUp(
                        funHeaderBuilder(
                            isOverridden = true,
                            name = "inspectPayloads",
                            args = listOf("payloads: Any?, instructor: $name, viewHolder: ${viewHolder.simpleName.getShortName()}?, doOnBind: () -> Unit")
                        )
                    )
                    appendUp("val payloadList = Novalles.extractPayload(payloads)")
                    appendIn("if (payloadList.isEmpty()) {")
                    appendUp("doOnBind()")
                    appendDown("}")
                    if (payloads.isNotEmpty()) {
                        appendIn("payloadList.forEach { payload -> ")
                        appendUp("when (payload) {")
                        incrementLevel()

                        payloads.forEach { payloading ->
                            val payName =
                                payloading.name.removeSuffix("Changed")
                            val inspectorFunc = inspectorFunctions.find {
                                it.arg.capitalizeFirst() == payName
                            }
                            val multipleFields = inspectorMultipleValues.find {
                                it.target.capitalizeFirst() == payName
                            }

                            val viewHolderAutoBinder =
                                viewHolderFun.find { it.simpleName.getShortName() == "$prefix${payName}" && it.parameters.size == 1 }


                            val action = when {

                                //BindOn without argument
                                inspectorFunc != null && inspectorFunc.isNullable == null -> "instructor.${inspectorFunc.name}()"

                                //BindOn with an bind argument
                                inspectorFunc != null && inspectorFunc.isBoolean == true -> {
                                    "instructor.${inspectorFunc.name}(false)"
                                }

                                //BindOnFields
                                multipleFields != null -> "instructor.${multipleFields.name}()"

                                //BindViewHolder
                                with(viewHolderAutoBinder) {
                                    this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$prefix${payName}"
                                } -> {
                                    "viewHolder?.$prefix${payName}(payload.new${payName})"
                                }

                                else -> "Unit".also {
                                    logger.warn(
                                        "There was no function found for UI field $payName. Put NonUIProperty or check all requirements: function name, arg nullability and etc.",
                                        classDeclaration
                                    )
                                }
                            }
                            appendIn("is ${payloading.name} -> $action")
                        }

                        tagFunctions.forEach {
                            appendIn("is ${it.second.declaration.qualifiedName?.asString()} -> instructor.${it.first}()")
                        }
                    }
                    closeFunctions(1)

                    //Bind block
                    newLine()
                    appendIn(
                        funHeaderBuilder(
                            isOverridden = true,
                            name = "bind",
                            args = listOf("model: ${model.simpleName.getShortName()}, viewHolder: ${viewHolder.simpleName.getShortName()}, instructor: $name")
                        )
                    )
                    incrementLevel()
                    cachedFields.mapNotNull { payloading ->
                        val payName = payloading.name.capitalizeFirst()
                        val modelFieldName = payloading.variableName ?: payloading.name

                        val inspectorFunc = inspectorFunctions.find {
                            it.arg.capitalizeFirst() == payName
                        }
                        val multipleFields = inspectorMultipleValues.find {
                            it.target.capitalizeFirst() == payName
                        }

                        val viewHolderBaseBindCondition = { function: KSFunctionDeclaration ->
                            function.simpleName.getShortName() == "$bindPrefix${payName}" && function.parameters.size == 1
                        }

                        val viewHolderPostBindCondition = { function: KSFunctionDeclaration ->
                            function.simpleName.getShortName() == "$prefix${payName}" && function.parameters.size == 1
                        }

                        val viewHolderBaseAutoBinder = viewHolderFun.find(viewHolderBaseBindCondition)
                        val viewHolderDefaultBinder = viewHolderFun.find(viewHolderPostBindCondition)

                        when {

                            //BindOn without argument
                            inspectorFunc != null && inspectorFunc.isNullable == null -> "instructor.${inspectorFunc.name}()"

                            //BindOn with bind argument
                            inspectorFunc != null && inspectorFunc.isBoolean == true -> {
                                "instructor.${inspectorFunc.name}(true)"
                            }

                            //BindOnFields
                            multipleFields != null -> "instructor.${multipleFields.name}()"

                            //BindViewHolder (binder)
                            with(viewHolderBaseAutoBinder) {
                                this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$bindPrefix${payName}"
                            } -> {
                                "viewHolder.$bindPrefix${payName}(model.${modelFieldName})"
                            }

                            //BindViewHolder
                            with(viewHolderDefaultBinder) {
                                this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$prefix${payName}"
                            } -> {
                                "viewHolder.$prefix${payName}(model.${modelFieldName})"
                            }

                            else -> null
                        }

                    }.toSet().forEach {
                        appendIn(it)
                    }
                    closeFunctions(0)
                }
            }

            val file = codeGenerator.createNewFile(
                dependencies,
                PACKAGE,
                "${model.simpleName.getShortName()}PayloadOfUIModel"
            )

            file.write(text.toByteArray())

            super.visitClassDeclaration(classDeclaration, data)
        }

        private fun extractViewHolderAnnotations(classDeclaration: KSClassDeclaration): Triple<KSClassDeclaration, String, String> {
            return when {
                classDeclaration.hasAnnotation(BindViewHolder::class) -> {
                    val annotation =
                        classDeclaration.findAnnotation(BindViewHolder::class) ?: errorHandler.throwUnexpectedError(classDeclaration)
                    val viewHolder = (annotation.arguments.retrieveArg<KSType>("viewHolder")).declaration.closestClassDeclaration()
                        ?: errorHandler.throwUnexpectedError(classDeclaration)
                    val prefix = annotation.arguments.retrieveArg<String>("prefix")
                    val postfix = annotation.arguments.retrieveArg<String>("bindPrefix")
                    Triple(viewHolder, prefix, postfix)
                }

                else -> {
                    errorHandler.logError(classDeclaration, "This class should be annotated with BindViewHolder annotation.")
                }
            }
        }

    }

    private inner class UIModelVisitor(val dependencies: Dependencies) : KSVisitorVoid() {


        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration, data: Unit
        ) {

            val errorHandler = ErrorHandler(logger)
            errorHandler.checkDataClassUIModel(classDeclaration)

            val constructor = classDeclaration.primaryConstructor ?: return

            val key = constructor.parameters.firstOrNull { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.PrimaryTag.name } != null
            } ?: classDeclaration.primaryConstructor?.parameters?.first() ?: return

            //TODO: check on the exact version
            //TODO: check if fields are registered
            //TODO: check annotation
            val notUI = constructor.parameters.filter { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.NonUIProperty.name } != null
            }

            val decomposedFields = constructor.parameters.filter { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.Decompose.name } != null
            }
            //logger.warn("${constructor.parameters.map { "$it to ${it.annotations.toList()}" }}")

            decomposedFields.forEach {
                errorHandler.checkDecomposedValue(classDeclaration, it)
            }

            val decomposedFieldsValues =
                decomposedFields.mapNotNull {
                    val resolvedType = it.type.resolve()
                    val ob = resolvedType.declaration.closestClassDeclaration() ?: return@mapNotNull null
                    DecomposedEncapsulation(
                        clazz = ob,
                        fieldName = it.name?.getShortName() ?: return@mapNotNull null,
                        params = ob.primaryConstructor?.parameters?.filterNot { parameter ->
                            parameter.annotations.find { annotation ->
                                annotation.shortName.getShortName() == KUIAnnotations.NonUIProperty.name
                            } != null
                        } ?: emptyList(),
                        nullable = resolvedType.isMarkedNullable
                    )
                }

            val fields = constructor.parameters.toList().filterNot { it == key }.filterNot { it in notUI || it in decomposedFields }
            val name = classDeclaration.simpleName.getShortName()
            val import = classDeclaration.qualifiedName?.asString()
            val payloadsBaseInterface = "BasePayload"

            val payList = mutableListOf<Payloading>()
            val cachedFields = mutableListOf<CachedField>()
            payloadsMap[name] = payList
            cachedFieldsMap[name] = cachedFields
            payList.addAll(
                fields.map {
                    val typeResolved = it.type.resolve()
                    cachedFields.add(
                        CachedField(
                            it.toString(),
                            typeResolved.isMarkedNullable
                        )
                    )
                    Payloading(
                        "${it.toString().capitalizeFirst()}Changed",
                        typeResolved.isMarkedNullable
                    )
                }
            )
            decomposedFieldsValues.forEach { parent ->
                payList.addAll(
                    parent.params.map {
                        val typeResolved = it.type.resolve()

                        cachedFields.add(
                            CachedField(
                                "${
                                    it.toString().capitalizeFirst()
                                }In${parent.fieldName.capitalizeFirst()}",
                                parent.nullable || typeResolved.isMarkedNullable,
                                "${parent.fieldName}.$it"
                            )
                        )

                        Payloading(
                            "${
                                it.toString().capitalizeFirst()
                            }In${parent.fieldName.capitalizeFirst()}Changed",
                            parent.nullable || typeResolved.isMarkedNullable
                        )
                    }
                )
            }

            val fileText = buildString {
                add("package $PACKAGE")
                newLine()

                val importsMap = mutableMapOf<String, String>()
                fields.forEach { parameter ->
                    if (!parameter.type.element.isPrimitive()) {

                        val paramType = parameter.type.resolve()

                        val clazz = paramType.declaration.qualifiedName?.asString()
                            ?: return@forEach
                        importsMap[paramType.declaration.qualifiedName?.getShortName()
                            ?: return@forEach] = clazz
                        parameter.type.element?.typeArguments?.forEach Typed@{
                            if (!it.type.isPrimitive()) {
                                val resolvedElement = it.type?.resolve()
                                val nameClass = resolvedElement?.declaration?.qualifiedName?.asString() ?: return@Typed
                                importsMap[resolvedElement.declaration.qualifiedName?.getShortName()
                                    ?: return@Typed] = nameClass
                            }
                        }
                    }
                }
                decomposedFieldsValues.forEach Decomposed@{ decomposedValue ->
                    decomposedValue.params.forEach { parameter ->
                        if (!parameter.type.element.isPrimitive()) {
                            val paramType = parameter.type.resolve()
                            val clazz = paramType.declaration.qualifiedName?.asString() ?: return@forEach
                            importsMap[paramType.declaration.qualifiedName?.getShortName() ?: return@forEach] = clazz
                        }

                        parameter.type.element?.typeArguments?.forEach Typed@{
                            if (!it.type.isPrimitive()) {
                                val elementResolved = it.type?.resolve()

                                val nameClass = elementResolved?.declaration?.qualifiedName?.asString() ?: return@Typed
                                importsMap[elementResolved.declaration.qualifiedName?.getShortName() ?: return@Typed] = nameClass
                            }
                        }
                    }
                }

                val catalogueIndexName = classDeclaration.qualifiedName!!.asString()
                val catalogueValueName = PACKAGE + ".${name}UIHelper()"

                catalogUIModels[catalogueIndexName] = catalogueValueName

                importsMap.values.forEach {
                    add("import $it")
                }

                add("import $import")
                add("import ${com.flexeiprata.novalles.interfaces.BasePayload::class.qualifiedName}")
                add("import androidx.annotation.Keep")
                add("import com.flexeiprata.novalles.interfaces.*")
                add("import androidx.recyclerview.widget.RecyclerView.ViewHolder")
                newLine()

                val payloadsName = "${name}Payloads"
                buildIn {
                    appendIn("@Keep")
                    appendIn("class ${name}UIHelper() : UIModelHelper<$name>{ ")
                    appendUp(
                        funHeaderBuilder(
                            isOverridden = true,
                            name = "areItemsTheSame",
                            returnType = "Boolean",
                            args = listOf("oldItem: $name", "newItem: Any")
                        )
                    )
                    appendUp("return newItem is $name && oldItem.$key == newItem.$key")
                    appendDown("}")
                    newLine()
                    appendIn(
                        funHeaderBuilder(
                            isOverridden = true,
                            name = "areContentsTheSame",
                            returnType = "Boolean",
                            args = listOf("oldItem: $name", "newItem: Any")
                        )
                    )
                    appendUp("return newItem is $name")

                    val comparisons = fields.map { "oldItem.$it == newItem.$it\n" }.plus(
                        decomposedFieldsValues.map { parent ->
                            parent.params.joinToString(
                                separator = "${getTabs()}&& "
                            ) {
                                "oldItem.${parent.fieldName}${parent.dot}$it == newItem.${parent.fieldName}${parent.dot}$it\n"
                            }
                        }
                    )

                    if (comparisons.isNotEmpty()) {
                        append(
                            comparisons.joinToString(
                                prefix = "${getTabs()}&& ",
                                separator = "${getTabs()}&& "
                            )
                        )
                    }
                    appendDown("}")


                    newLine()
                    appendIn(
                        funHeaderBuilder(
                            isOverridden = true,
                            extension = null,
                            name = "changePayloads",
                            returnType = "MutableList<$payloadsBaseInterface>",
                            args = listOf("oldItem: $name", "newItem: Any")
                        )
                    )
                    if (fields.isNotEmpty() || decomposedFields.isNotEmpty()) {
                        appendUp("return mutableListOf<$payloadsBaseInterface>().apply {")
                        appendUp("if (newItem is $name) {")
                        incrementLevel()
                        fields.forEach {
                            appendIn(" if (newItem.$it != oldItem.$it) {")
                            appendUp(
                                "add($payloadsName.${
                                    it.toString().capitalizeFirst()
                                }Changed(newItem.$it))"
                            )
                            appendDown("}")
                        }
                        decomposedFieldsValues.forEach { parent ->
                            parent.params.forEach {
                                appendIn(" if (newItem.${parent.fieldName}${parent.dot}$it != oldItem.${parent.fieldName}${parent.dot}$it) {")
                                appendUp(
                                    "add($payloadsName.${
                                        it.toString().capitalizeFirst()
                                    }In${parent.fieldName.capitalizeFirst()}Changed(newItem.${parent.fieldName}${parent.dot}$it))"
                                )
                                appendDown("}")
                            }
                        }
                    } else {
                        appendUp("return mutableListOf()")
                        appendDown("}")
                    }
                    closeFunctions(1)
                    newLine()

                    appendIn("@Suppress(\"UNCHECKED_CAST\")")
                    appendIn(
                        funHeaderBuilder(
                            isOverridden = true,
                            genericString = "<R: BasePayload>",
                            name = "changePayloadsMap",
                            returnType = "List<R>",
                            args = listOf("oldItem: $name", "newItem: Any")
                        )
                    )
                    appendUp("return changePayloads(oldItem, newItem).map {it as R} ")
                    closeFunctions(0)
                }
                newLine(2)

                if (fields.isNotEmpty() || decomposedFields.isNotEmpty()) {
                    buildIn {
                        val header = "sealed class $payloadsName : $payloadsBaseInterface {"
                        appendIn(header)
                        incrementLevel()
                        fields.forEach {
                            appendIn(
                                dataClassConstructor(
                                    name = "${it.toString().capitalizeFirst()}Changed",
                                    parent = "${name}Payloads()",
                                    "new${it.toString().capitalizeFirst()}: ${it.writeAsVariable()}"
                                )
                            )
                        }
                        decomposedFieldsValues.forEach { parent ->
                            parent.params.forEach {
                                appendIn(
                                    dataClassConstructor(
                                        name = "${
                                            it.toString().capitalizeFirst()
                                        }In${parent.fieldName.capitalizeFirst()}Changed",
                                        parent = "${name}Payloads()",
                                        "new${
                                            it.toString().capitalizeFirst()
                                        }In${parent.fieldName.capitalizeFirst()}: ${
                                            it.writeAsVariable(
                                                parent
                                            )
                                        }"
                                    )
                                )
                            }
                        }
                        appendDown("}")
                    }
                    newLine(2)
                }
            }


            val file = codeGenerator.createNewFile(
                dependencies,
                PACKAGE,
                "${name}UIModelInterfaces"
            )

            file.write(fileText.toByteArray())

        }
    }

}


