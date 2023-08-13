package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.*
import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.utils.*
import com.flexeiprata.novalles.utils.writingtools.*
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.*
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

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(UIModel::class.qualifiedName!!)
        val instructors = resolver.getSymbolsWithAnnotation(Instruction::class.qualifiedName!!)

        val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())

        symbols.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(UIModelVisitor(dependencies), Unit) }

        instructors.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(ViewHoldersVisitor(dependencies), Unit) }

        return symbols.plus(instructors).filterNot { it.validate() }.toList()
    }

    private inner class ViewHoldersVisitor(val dependencies: Dependencies) : KSVisitorVoid() {

        val errorHandler = ErrorHandler(logger)



        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            fun throwUnexpected(): Nothing = errorHandler.throwUnexpectedError(classDeclaration)

            errorHandler.checkInspector(classDeclaration)

            val core = classDeclaration.findAnnotation(Instruction::class) ?: throwUnexpected()
            val model = (core.arguments.first().value as KSType).declaration.closestClassDeclaration() ?: throwUnexpected()
            val (viewHolder, prefix, postfix) = extractViewHolderAnnotations(classDeclaration)
            val bindPrefix = "bind" //May be customisable in future
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
                                viewHolderFun.find { it.simpleName.getShortName() == "$prefix${payName}$postfix" && it.parameters.size == 1 }


                            val action = when {

                                //BindOn without argument
                                inspectorFunc != null && inspectorFunc.isNullable == null -> "instructor.${inspectorFunc.name}()"

                                //BindOn with an bind argument
                                inspectorFunc != null && inspectorFunc.isBoolean == true  -> {
                                    "instructor.${inspectorFunc.name}(false)"
                                }

                                //BindOnFields
                                multipleFields != null -> "instructor.${multipleFields.name}()"

                                //BindViewHolder
                                with(viewHolderAutoBinder) {
                                    this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$prefix${payName}$postfix"
                                } -> {
                                    "viewHolder?.$prefix${payName}$postfix(payload.new${payName})"
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
                            function.simpleName.getShortName() == "$bindPrefix${payName}$postfix" && function.parameters.size == 1
                        }

                        val viewHolderPostBindCondition = { function: KSFunctionDeclaration ->
                            function.simpleName.getShortName() == "$prefix${payName}$postfix" && function.parameters.size == 1
                        }

                        val viewHolderBaseAutoBinder = viewHolderFun.find(viewHolderBaseBindCondition)
                        val viewHolderDefaultBinder = viewHolderFun.find(viewHolderPostBindCondition)

                        //TODO: Bind on with boolean argument
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
                                this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$bindPrefix${payName}$postfix"
                            } -> {
                                "viewHolder.$bindPrefix${payName}$postfix(model.${modelFieldName})"
                            }

                            //BindViewHolder
                            with(viewHolderDefaultBinder) {
                                this != null && isFirstArgNullable() == payloading.isNullable && simpleName.getShortName() == "$prefix${payName}$postfix"
                            } -> {
                                "viewHolder.$prefix${payName}$postfix(model.${modelFieldName})"
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

        @Suppress("DEPRECATION")
        private fun extractViewHolderAnnotations(classDeclaration: KSClassDeclaration): Triple<KSClassDeclaration, String, String> {
            return when {
                classDeclaration.hasAnnotation(BindViewHolder::class) -> {
                    val annotation = classDeclaration.findAnnotation(BindViewHolder::class) ?: errorHandler.throwUnexpectedError(classDeclaration)
                    val viewHolder = (annotation.arguments.retrieveArg<KSType>("viewHolder")).declaration.closestClassDeclaration() ?: errorHandler.throwUnexpectedError(classDeclaration)
                    val prefix = annotation.arguments.retrieveArg<String>("prefix")
                    val postfix = annotation.arguments.retrieveArg<String>("postfix")
                    Triple(viewHolder, prefix, postfix)
                }
                classDeclaration.hasAnnotation(AutoBindViewHolder::class) -> {
                    val viewHolder = (classDeclaration.findAnnotation(AutoBindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.closestClassDeclaration()
                    Triple(viewHolder ?: errorHandler.throwUnexpectedError(classDeclaration), "set", "")
                }
                else -> {
                    errorHandler.logError(classDeclaration, "This class should be annotated with BindViewHolder annotation.")
                }
            }
        }

    }

    private inner class UIModelVisitor(val dependencies: Dependencies) : KSVisitorVoid() {

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration, data: Unit
        ) {

            val errorHandler = ErrorHandler(logger)
            errorHandler.checkDataClassUIModel(classDeclaration)

            val constructor = classDeclaration.primaryConstructor ?: return

            val key = constructor.parameters.firstOrNull { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.PrimaryTag.name } != null
            } ?: classDeclaration.primaryConstructor?.parameters?.first() ?: return

            val notUI = constructor.parameters.filter { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.NonUIProperty.name } != null
            }

            val decomposedFields = constructor.parameters.filter { parameter ->
                parameter.annotations.find { it.shortName.getShortName() == KUIAnnotations.Decompose.name } != null
            }

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


