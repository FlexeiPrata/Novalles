package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.BindOnFields
import com.flexeiprata.novalles.annotations.BindOnTag
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.NovallesCatalogue
import com.flexeiprata.novalles.annotations.NovallesPage
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.interfaces.UIModelHelper
import com.flexeiprata.novalles.utils.CachedField
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
import com.flexeiprata.novalles.utils.writingtools.firstWinsBy
import com.flexeiprata.novalles.utils.writingtools.funHeaderBuilder
import com.flexeiprata.novalles.utils.writingtools.isFirstArgNullable
import com.flexeiprata.novalles.utils.writingtools.lowercaseFirst
import com.flexeiprata.novalles.utils.writingtools.newLine
import com.flexeiprata.novalles.utils.writingtools.retrieveArg
import com.flexeiprata.novalles.utils.writingtools.writeAsVariable
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

class NovallesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    private val payloadsMap = mutableMapOf<String, MutableList<Payloading>>()
    private val cachedFieldsMap = mutableMapOf<String, MutableList<CachedField>>()
    private val catalogUIModels = mutableMapOf<String, String>()
    private val catalogUIModelFiles = mutableSetOf<KSFile>()
    private val catalogInspectors = mutableMapOf<String, String>()
    private val catalogInspectorFiles = mutableSetOf<KSFile>()

    /**
     * Escape hatch: `ksp { arg("novalles.safeDependencies", "true") }` restores the previous
     * behaviour of associating every output with every source file. It disables incremental
     * processing, so use it only to work around a suspected dependency bug.
     */
    private val safeDependencies = options["novalles.safeDependencies"] == "true"

    private fun dependenciesOf(aggregating: Boolean, vararg files: KSFile?): Dependencies {
        if (safeDependencies) return Dependencies.ALL_FILES
        return Dependencies(aggregating, *files.filterNotNull().distinct().toTypedArray())
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val symbols = resolver.getSymbolsWithAnnotation(UIModel::class.qualifiedName!!).toList()
        val instructors =
            resolver.getSymbolsWithAnnotation(Instruction::class.qualifiedName!!).toList()
        val pages = resolver.getSymbolsWithAnnotation(NovallesPage::class.qualifiedName!!)
            .firstOrNull() as? KSClassDeclaration?
        val catalogue = resolver.getSymbolsWithAnnotation(NovallesCatalogue::class.qualifiedName!!)
            .firstOrNull() as? KSClassDeclaration?

        if (symbols.isEmpty() && instructors.isEmpty() && catalogue == null) return emptyList()

        //Validate once per symbol: validate() resolves every reachable type reference.
        val validatedSymbols = symbols.map { it to it.validate() }
        val validatedInstructors = instructors.map { it to it.validate() }

        validatedSymbols.forEach { (symbol, isValid) ->
            if (isValid && symbol is KSClassDeclaration) {
                symbol.accept(UIModelVisitor(), Unit)
            }
        }

        validatedInstructors.forEach { (instructor, isValid) ->
            if (isValid && instructor is KSClassDeclaration) {
                instructor.accept(ViewHoldersVisitor(), Unit)
            }
        }

        if (pages != null) {
            createPage(pages)
        }

        if (catalogue != null) {
            createCatalogue(catalogue)
        }

        val deferred = (validatedSymbols + validatedInstructors)
            .filterNot { (_, isValid) -> isValid }
            .map { (symbol, _) -> symbol }

        return if (catalogue != null && !catalogue.validate()) deferred + catalogue else deferred
    }

    private fun createPage(pages: KSClassDeclaration) {
        val fileString = buildString {
            buildIn {
                appendIn("package $CATALOGUES_PACKAGE")
                newLine()
                appendIn("import ${UIModelHelper::class.qualifiedName}")
                appendIn("import kotlin.reflect.KClass")
                appendIn("import com.flexeiprata.novalles.interfaces.CataloguePage")
                appendIn("import androidx.annotation.Keep")
                appendIn("import com.flexeiprata.novalles.interfaces.Inspector")
                appendIn("import com.flexeiprata.novalles.interfaces.Instructor")
                newLine()
                appendIn("@Keep")
                appendIn("class ${pages.simpleName.getShortName()}: CataloguePage { ")
                newLine()
                newLine()
                incrementLevel()
                appendIn("@Suppress(\"UNCHECKED_CAST\")")
                appendIn(
                    funHeaderBuilder(
                        name = "eligible",
                        args = listOf("clazz: KClass<*>"),
                        returnType = "Boolean",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("return when (clazz) {")
                incrementLevel()
                val classesList = catalogUIModels.keys.joinToString(
                    separator = ",\n$tabs",
                    prefix = "",
                    postfix = "",
                    truncated = "",
                ) {
                    "${it}::class"
                }
                appendIn("$classesList -> true")
                appendIn("else -> false")
                closeFunctions(1)
                newLine()
                appendIn("@Suppress(\"UNCHECKED_CAST\")")
                appendIn(
                    funHeaderBuilder(
                        name = "provideUiModel",
                        genericString = "<T>",
                        args = listOf("clazz: KClass<*>"),
                        returnType = "UIModelHelper<T>?",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when (clazz) {")
                incrementLevel()
                for ((key, value) in catalogUIModels) {
                    appendIn("$key::class -> $value")
                }
                appendIn("else -> return null")
                appendDown("}")
                appendIn("return helper as UIModelHelper<T>")
                appendDown("}")
                newLine()
                appendIn("@Suppress(\"UNCHECKED_CAST\")")
                appendIn(
                    funHeaderBuilder(
                        name = "provideInspector",
                        genericString = "<T: Instructor>",
                        args = listOf("clazz: KClass<*>"),
                        returnType = "Inspector<T, Any, Any>?",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when (clazz) {")
                incrementLevel()
                for ((key, _) in catalogUIModels) {
                    val inspector = catalogInspectors[key] ?: continue
                    appendIn("$key::class -> $inspector")
                }
                appendIn("else -> return null")
                appendDown("}")
                appendIn("return helper as Inspector<T, Any, Any>")
                appendDown("}")
                closeFunctions()
            }
        }
        //Aggregating: this page lists every @UIModel and every generated Inspector, so any new symbol must invalidate it.
        val file = codeGenerator.createNewFile(
            dependenciesOf(
                aggregating = true,
                pages.containingFile,
                *catalogUIModelFiles.toTypedArray(),
                *catalogInspectorFiles.toTypedArray()
            ),
            CATALOGUES_PACKAGE,
            pages.simpleName.getShortName()
        )
        file.write(fileString.toByteArray())
    }

    private fun createCatalogue(catalogue: KSClassDeclaration) {
        val pagesRaw = catalogue.findAnnotation(NovallesCatalogue::class)?.arguments?.first()?.value as ArrayList<*>
        val pages = pagesRaw.filterIsInstance<KSType>()

        //UI model catalogue
        val fileString = buildString {
            buildIn {
                appendIn("package $CATALOGUES_PACKAGE")
                newLine()
                appendIn("import ${UIModelHelper::class.qualifiedName}")
                appendIn("import kotlin.reflect.KClass")
                appendIn("import com.flexeiprata.novalles.interfaces.Catalogue")
                appendIn("import androidx.annotation.Keep")
                appendIn("import com.flexeiprata.novalles.interfaces.Inspector")
                appendIn("import com.flexeiprata.novalles.interfaces.Instructor")
                pages.mapNotNull { it.declaration.qualifiedName }.forEach { page ->
                    appendIn("import $CATALOGUES_PACKAGE.${page.getShortName()}")
                }
                newLine()
                appendIn("@Keep")
                appendIn("class NovallesCatalogue: Catalogue { ")
                newLine()
                incrementLevel()
                newLine()
                //Values
                pages.forEach {
                    appendIn("val ${it.declaration.simpleName.getShortName().lowercaseFirst()} = ${it.declaration.simpleName.getShortName()}()")
                }
                newLine()
                appendIn("@Suppress(\"UNCHECKED_CAST\")")
                appendIn(
                    funHeaderBuilder(
                        name = "provideUiModel",
                        genericString = "<T>",
                        args = listOf("clazz: KClass<*>"),
                        returnType = "UIModelHelper<T>?",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when {")
                incrementLevel()
                pages.forEach { page ->
                    appendIn("${page.declaration.simpleName.getShortName().lowercaseFirst()}.eligible(clazz) -> ${page.declaration.simpleName.getShortName().lowercaseFirst()}.provideUiModel<T>(clazz)")
                }
                appendIn("else -> return null")
                appendDown("}")
                appendIn("return helper as UIModelHelper<T>")
                appendDown("}")
                newLine()
                appendIn("@Suppress(\"UNCHECKED_CAST\")")
                appendIn(
                    funHeaderBuilder(
                        name = "provideInspector",
                        genericString = "<T: Instructor>",
                        args = listOf("clazz: KClass<*>"),
                        returnType = "Inspector<T, Any, Any>?",
                        isOverridden = true
                    )
                )
                incrementLevel()
                appendIn("val helper = when {")
                incrementLevel()
                pages.forEach { page ->
                    appendIn("${page.declaration.simpleName.getShortName().lowercaseFirst()}.eligible(clazz) -> ${page.declaration.simpleName.getShortName().lowercaseFirst()}.provideInspector<T>(clazz)")
                }
                appendIn("else -> return null")
                appendDown("}")
                appendIn("return helper as Inspector<T, Any, Any>")
                appendDown("}")
                closeFunctions()
            }
        }

        //Aggregating: a newly added page must invalidate the root catalogue.
        val file = codeGenerator.createNewFile(
            dependenciesOf(
                aggregating = true,
                catalogue.containingFile,
                *pages.map { it.declaration.containingFile }.toTypedArray()
            ),
            CATALOGUES_PACKAGE,
            "NovallesCatalogue"
        )
        file.write(fileString.toByteArray())
    }

    private inner class ViewHoldersVisitor : KSVisitorVoid() {

        val errorHandler = ErrorHandler(logger)


        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            fun throwUnexpected(): Nothing = errorHandler.throwUnexpectedError(classDeclaration)

            errorHandler.checkInspector(classDeclaration)

            val core = classDeclaration.findAnnotation(Instruction::class) ?: throwUnexpected()
            val model = (core.arguments.first().value as KSType).declaration.closestClassDeclaration() ?: throwUnexpected()
            val (viewHolder, prefix, bindPrefix) = extractViewHolderAnnotations(classDeclaration)
            val name = classDeclaration.simpleName.getShortName()

            //Register this model's Inspector for the catalogue; reject a second @Instruction for the same model.
            val modelQualifiedName = model.qualifiedName?.asString() ?: throwUnexpected()
            if (catalogInspectors.containsKey(modelQualifiedName)) {
                errorHandler.logError(
                    classDeclaration,
                    "Model ${model.simpleName.getShortName()} already has an Instruction. Only one @Instruction is allowed per UI model."
                )
            }
            catalogInspectors[modelQualifiedName] = "$PACKAGE.${model.simpleName.getShortName()}Inspector()"
            classDeclaration.containingFile?.let(catalogInspectorFiles::add)

            //Materialized once: getAllFunctions() walks the whole supertype closure.
            val declarationFunctions = classDeclaration.getAllFunctions().toList()

            val inspectorFunctions = declarationFunctions.mapNotNull { function ->
                val annotation = function.findAnnotation(BindOn::class) ?: return@mapNotNull null
                InspectorFunData(
                    name = function.simpleName.getShortName(),
                    arg = annotation.arguments.first().value as String,
                    isNullable = function.parameters.firstOrNull()?.type?.resolve()?.isMarkedNullable,
                    isBoolean = function.parameters.firstOrNull()?.type?.toString() == "Boolean"
                )
            }

            val inspectorMultipleValues = declarationFunctions.mapNotNull { function ->
                val annotation = function.findAnnotation(BindOnFields::class) ?: return@mapNotNull null
                val fields = annotation.arguments.first().value as ArrayList<*>? ?: throwUnexpected()
                fields.filterIsInstance<String>().map {
                    InspectorFunDataFlat(
                        name = function.simpleName.getShortName(),
                        target = it
                    )
                }
            }.flatten()

            val tagFunctions = declarationFunctions.mapNotNull { function ->
                val annotation =
                    function.findAnnotation(BindOnTag::class) ?: return@mapNotNull null
                function.simpleName.getShortName() to annotation.arguments.first().value as KSType
            }

            //Indexed by the name the generated code looks up, keeping find{}'s first-wins semantics.
            val inspectorFunctionsByField = inspectorFunctions.firstWinsBy { it.arg.capitalizeFirst() }
            val inspectorMultipleByField = inspectorMultipleValues.firstWinsBy { it.target.capitalizeFirst() }

            //Name -> is-first-arg-nullable. Resolves each binder's first parameter exactly once.
            val viewHolderBinders = viewHolder.getAllFunctions()
                .filter { it.isPublic() && it.parameters.size == 1 }
                .toList()
                .firstWinsBy { it.simpleName.getShortName() }
                .mapValues { (_, function) -> function.isFirstArgNullable() }

            val payloads = payloadsMap[model.simpleName.getShortName()] ?: emptyList()
            val cachedFields = cachedFieldsMap[model.simpleName.getShortName()] ?: emptyList()

            //Imports
            val listOfImports = listOfNotNull(
                viewHolder.qualifiedName?.asString(),
                if (payloads.isNotEmpty()) "$PACKAGE.${model.simpleName.getShortName()}Payloads.*" else null,
                classDeclaration.qualifiedName?.asString(),
                Inspector::class.qualifiedName,
                modelQualifiedName,
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
                    if (payloads.isNotEmpty() || tagFunctions.isNotEmpty()) {
                        appendIn("payloadList.forEach { payload -> ")
                        appendUp("when (payload) {")
                        incrementLevel()

                        payloads.forEach { payloading ->
                            val payName =
                                payloading.name.removeSuffix("Changed")
                            val inspectorFunc = inspectorFunctionsByField[payName]
                            val multipleFields = inspectorMultipleByField[payName]

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
                                viewHolderBinders["$prefix${payName}"] == payloading.isNullable -> {
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
                        val modelFieldName = payloading.name

                        val inspectorFunc = inspectorFunctionsByField[payName]
                        val multipleFields = inspectorMultipleByField[payName]

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
                            viewHolderBinders["$bindPrefix${payName}"] == payloading.isNullable -> {
                                "viewHolder.$bindPrefix${payName}(model.${modelFieldName})"
                            }

                            //BindViewHolder
                            viewHolderBinders["$prefix${payName}"] == payloading.isNullable -> {
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
                dependenciesOf(
                    aggregating = false,
                    classDeclaration.containingFile,
                    model.containingFile,
                    viewHolder.containingFile,
                    *tagFunctions.map { it.second.declaration.containingFile }.toTypedArray()
                ),
                PACKAGE,
                "${model.simpleName.getShortName()}PayloadOfUIModel"
            )

            file.write(text.toByteArray())

            super.visitClassDeclaration(classDeclaration, data)
        }

        private fun extractViewHolderAnnotations(classDeclaration: KSClassDeclaration): Triple<KSClassDeclaration, String, String> {
            return when (val annotation = classDeclaration.findAnnotation(BindViewHolder::class)) {
                null -> {
                    errorHandler.logError(
                        classDeclaration,
                        "This class should be annotated with BindViewHolder annotation."
                    )
                }

                else -> {
                    val viewHolder =
                        (annotation.arguments.retrieveArg<KSType>("viewHolder")).declaration.closestClassDeclaration()
                            ?: errorHandler.throwUnexpectedError(classDeclaration)
                    val prefix = annotation.arguments.retrieveArg<String>("prefix")
                    val postfix = annotation.arguments.retrieveArg<String>("bindPrefix")
                    Triple(viewHolder, prefix, postfix)
                }
            }
        }

    }

    private inner class UIModelVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(
            classDeclaration: KSClassDeclaration, data: Unit
        ) {

            val errorHandler = ErrorHandler(logger)
            errorHandler.checkDataClassUIModel(classDeclaration)

            val constructor = classDeclaration.primaryConstructor ?: return

            //Single pass over the parameters: each annotation sequence is walked once.
            var primaryTag: KSValueParameter? = null
            val notUI = HashSet<KSValueParameter>()
            constructor.parameters.forEach { parameter ->
                parameter.annotations.forEach { annotation ->
                    when (annotation.shortName.getShortName()) {
                        KUIAnnotations.PrimaryTag.name -> if (primaryTag == null) primaryTag = parameter
                        KUIAnnotations.NonUIProperty.name -> notUI.add(parameter)
                    }
                }
            }

            val key = primaryTag ?: constructor.parameters.firstOrNull() ?: return

            val fields = constructor.parameters.filterNot { it == key || it in notUI }
            val name = classDeclaration.simpleName.getShortName()
            val import = classDeclaration.qualifiedName?.asString()
            val payloadsBaseInterface = "BasePayload"

            //Resolve each field type once; both the payload list and the imports need it.
            val resolvedFields = fields.map { it to it.type.resolve() }

            val payList = mutableListOf<Payloading>()
            val cachedFields = mutableListOf<CachedField>()
            payloadsMap[name] = payList
            cachedFieldsMap[name] = cachedFields
            payList.addAll(
                resolvedFields.map { (parameter, typeResolved) ->
                    cachedFields.add(
                        CachedField(
                            parameter.toString(),
                            typeResolved.isMarkedNullable
                        )
                    )
                    Payloading(
                        "${parameter.toString().capitalizeFirst()}Changed",
                        typeResolved.isMarkedNullable
                    )
                }
            )
            val fileText = buildString {
                add("package $PACKAGE")
                newLine()

                val importsMap = mutableMapOf<String, String>()
                resolvedFields.forEach { (parameter, paramType) ->
                    if (!parameter.type.element.isPrimitive()) {
                        val clazz = paramType.declaration.qualifiedName?.asString()
                            ?: return@forEach
                        importsMap[paramType.declaration.qualifiedName?.getShortName()
                            ?: return@forEach] = clazz
                        parameter.type.element?.typeArguments?.forEach Typed@{
                            if (!it.type.isPrimitive()) {
                                val resolvedElement = it.type?.resolve()
                                val nameClass =
                                    resolvedElement?.declaration?.qualifiedName?.asString()
                                        ?: return@Typed
                                importsMap[resolvedElement.declaration.qualifiedName?.getShortName()
                                    ?: return@Typed] = nameClass
                            }
                        }
                    }
                }
                val catalogueIndexName = classDeclaration.qualifiedName!!.asString()
                val catalogueValueName = PACKAGE + ".${name}UIHelper()"

                catalogUIModels[catalogueIndexName] = catalogueValueName
                classDeclaration.containingFile?.let(catalogUIModelFiles::add)

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

                    val comparisons = fields.map { "oldItem.$it == newItem.$it\n" }

                    if (comparisons.isNotEmpty()) {
                        append(
                            comparisons.joinToString(
                                prefix = "${tabs}&& ",
                                separator = "${tabs}&& "
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
                    if (fields.isNotEmpty()) {
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

                if (fields.isNotEmpty()) {
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
                        appendDown("}")
                    }
                    newLine(2)
                }
            }


            val file = codeGenerator.createNewFile(
                dependenciesOf(aggregating = false, classDeclaration.containingFile),
                PACKAGE,
                "${name}UIModelInterfaces"
            )

            file.write(fileText.toByteArray())

        }
    }

}


