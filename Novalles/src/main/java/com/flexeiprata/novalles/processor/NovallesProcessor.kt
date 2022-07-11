package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.AutoBindViewHolder
import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.utils.*
import com.flexeiprata.novalles.utils.writingtools.*
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

class NovallesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val payloadsMap = mutableMapOf<String, MutableList<Payloading>>()

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(UIModel::class.qualifiedName!!)
        val instructors = resolver.getSymbolsWithAnnotation(Instruction::class.qualifiedName!!)


        val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())

        symbols.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(MyEventKClassVisitor(dependencies), Unit) }

        instructors.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(ViewHoldersVisitor(dependencies), Unit) }

        return symbols.plus(instructors).filterNot { it.validate() }.toList()
    }

    private inner class ViewHoldersVisitor(val dependencies: Dependencies) : KSVisitorVoid() {


        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            val errorHandler = ErrorHandler(logger)
            errorHandler.checkInspector(classDeclaration)

            val core = classDeclaration.findAnnotation(Instruction::class) ?: return
            val model =
                (core.arguments.first().value as KSType).declaration.closestClassDeclaration()
                    ?: return
            val viewHolder =
                (classDeclaration.findAnnotation(AutoBindViewHolder::class)?.arguments?.first()?.value as KSType?)?.declaration?.closestClassDeclaration()
            val name = classDeclaration.simpleName.getShortName()

            val inspectorFunctions =
                classDeclaration.getAllFunctions().filter {
                    it.findAnnotation(BindOn::class) != null
                }.map {
                    InspectorFunData(
                        name = it.simpleName.getShortName(),
                        arg = it.annotations.first().arguments.first().value as String,
                        isNullable = it.parameters.first().type.resolve().isMarkedNullable
                    )
                }.toList()

            val viewHolderFun = viewHolder?.getAllFunctions()?.filterNot { !it.isPublic() } ?: emptySequence()
            val payloads = payloadsMap[model.simpleName.getShortName()] ?: emptyList()

            //Imports
            val listOfImports = listOfNotNull(
                "androidx.recyclerview.widget.RecyclerView.ViewHolder",
                viewHolder?.qualifiedName?.asString(),
                "$PACKAGE.${model.simpleName.getShortName()}Payloads.*",
                classDeclaration.qualifiedName?.asString(),
                Inspector::class.qualifiedName,
                "androidx.annotation.Keep"
            )

            val text = buildString {
                add("package $PACKAGE")
                listOfImports.forEach {
                    add("import $it")
                }
                newLine(2)

                buildIn {
                    appendIn("@Keep")
                    append("class ${name}Impl(val instructor: $name, val viewHolder: ${viewHolder?.simpleName?.getShortName() ?: "ViewHolder"}) : Inspector { ")
                    newLine(1)
                    appendUp(
                        funHeaderBuilder(
                            isOverridden = true,
                            name = "inspectPayloads",
                            args = listOf("payloads: List<Any>")
                        )
                    )
                    appendUp("payloads.forEach { payload -> ")
                    appendUp("when (payload) {")
                    incrementLevel()

                    payloads.forEach { payloading ->
                        val payName =
                            payloading.name.removeSuffix("Changed")
                        val inspectorFunc = inspectorFunctions.find {
                            it.arg.capitalizeFirst() == payName
                        }

                        val viewHolderAutoBinder =
                            viewHolderFun.find { it.simpleName.getShortName() == "set${payName}" && it.parameters.size == 1}


                        val action = when {
                            inspectorFunc != null && inspectorFunc.isNullable == payloading.isNullable -> "instructor.${inspectorFunc.name}(payload.new${payName})"
                            viewHolderAutoBinder != null && viewHolderAutoBinder.isFirstArgNullable() == payloading.isNullable -> "viewHolder.set${payName}(payload.new${payName})"
                            else -> "Unit".also {
                                if (viewHolder != null) {
                                    logger.warn(
                                        "AutoBinding is on, but there was no function found for param $payName. Check Names and nullability params.",
                                        classDeclaration
                                    )
                                }
                            }
                        }
                        appendIn("is ${payloading.name} -> $action")
                    }
                    closeFunctions(0)
                }
            }

            val file = codeGenerator.createNewFile(
                dependencies,
                PACKAGE,
                "${name}PayloadOfUIModel"
            )

            file.write(text.toByteArray())

            super.visitClassDeclaration(classDeclaration, data)
        }

    }

    private inner class MyEventKClassVisitor(val dependencies: Dependencies) : KSVisitorVoid() {

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


            val decomposedFieldsValues =
                decomposedFields.map {
                    val ob =
                        it.type.resolve().declaration.closestClassDeclaration() ?: return@map null
                    DecomposedEncapsulation(
                        clazz = ob,
                        fieldName = it.name?.getShortName() ?: return@map null,
                        params = ob.primaryConstructor?.parameters?.filterNot { parameter ->
                            parameter.annotations.find { annotation ->
                                annotation.shortName.getShortName() == KUIAnnotations.NonUIProperty.name
                            } != null
                        } ?: emptyList(),
                        nullable = it.type.resolve().isMarkedNullable
                    )
                }.filterNotNull()

            val fields =
                constructor.parameters.toList().filterNot { it == key }
                    .filterNot { it in notUI || it in decomposedFields }


            val name = classDeclaration.simpleName.getShortName()
            val import = classDeclaration.qualifiedName?.asString()
            val payloadsBaseInterface = "BasePayload"

            val payList = mutableListOf<Payloading>()
            payloadsMap[name] = payList
            payList.addAll(
                fields.map {
                    Payloading(
                        "${it.toString().capitalizeFirst()}Changed",
                        it.type.resolve().isMarkedNullable
                    )
                }
            )
            decomposedFieldsValues.forEach { parent ->
                payList.addAll(
                    parent.params.map {
                        Payloading(
                            "${
                                it.toString().capitalizeFirst()
                            }In${parent.fieldName.capitalizeFirst()}Changed",
                            it.type.resolve().isMarkedNullable || parent.nullable
                        )
                    }
                )
            }

            val fileText = buildString {
                add("package $PACKAGE")
                newLine()

                val importsMap = mutableMapOf<String, String>()

                fields.forEach {
                    if (!it.type.element.isPrimitive()) {
                        val clazz = it.type.resolve().declaration.qualifiedName?.asString()
                            ?: return@forEach
                        importsMap[it.type.resolve().declaration.qualifiedName?.getShortName()
                            ?: return@forEach] = clazz
                    }
                }
                decomposedFieldsValues.forEach Decomposed@{
                    it.params.forEach { parameter ->
                        if (!parameter.type.element.isPrimitive()) {
                            val clazz = parameter.type.resolve().declaration.qualifiedName?.asString()
                                ?: return@forEach
                            importsMap[parameter.type.resolve().declaration.qualifiedName?.getShortName()
                                ?: return@forEach] = clazz
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

                    append(
                        comparisons.joinToString(
                            prefix = "${getTabs()}&& ",
                            separator = "${getTabs()}&& "
                        )
                    )
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
                    closeFunctions(1)

                    appendIn("@Suppress(\"UNCHECKED_CAST\")")
                    appendIn(funHeaderBuilder(
                        isOverridden = true,
                        genericString = "<R: BasePayload>",
                        name = "changePayloadsMap",
                        returnType = "List<R>",
                        args = listOf("oldItem: $name", "newItem: Any")
                    ))
                    appendUp("return changePayloads(oldItem, newItem).map {it as R} ")
                    closeFunctions(0)
                }
                newLine(2)

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


            val file = codeGenerator.createNewFile(
                dependencies,
                PACKAGE,
                "${name}UIModelInterfaces"
            )

            file.write(fileText.toByteArray())

        }
    }

}


