package com.flexeiprata.novalles.processor

import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.BindOnFields
import com.flexeiprata.novalles.annotations.BindOnTag
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.CatalogueOnly
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.NovallesCatalogue
import com.flexeiprata.novalles.annotations.NovallesPage
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Inspector
import com.flexeiprata.novalles.interfaces.UIModelHelper
import com.flexeiprata.novalles.utils.CachedField
import com.flexeiprata.novalles.utils.InspectorFunData
import com.flexeiprata.novalles.utils.InspectorFunDataFlat
import com.flexeiprata.novalles.utils.Payloading
import com.flexeiprata.novalles.utils.uiFields
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
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

class NovallesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    private val catalogUIModels = mutableMapOf<String, String>()
    private val catalogUIModelFiles = mutableSetOf<KSFile>()
    private val catalogInspectors = mutableMapOf<String, String>()
    private val catalogInspectorFiles = mutableSetOf<KSFile>()
    private val catalogInstructors = mutableMapOf<String, String>()
    private var pageSeen = false

    /**
     * Escape hatch: `ksp { arg("novalles.safeDependencies", "true") }` restores the previous
     * behaviour of associating every output with every source file. It disables incremental
     * processing, so use it only to work around a suspected dependency bug.
     */
    private val safeDependencies = options["novalles.safeDependencies"] == "true"

    /**
     * `ksp { arg("novalles.strictCatalogue", "true") }` forbids the reflection fallback: setups that would need it fail
     * the build, generated classes get [CatalogueOnly], and the generated catalogue throws instead of returning null.
     */
    private val strictCatalogue = options["novalles.strictCatalogue"] == "true"

    private fun dependenciesOf(aggregating: Boolean, vararg files: KSFile?): Dependencies {
        if (safeDependencies) return Dependencies.ALL_FILES
        return Dependencies(aggregating, *files.filterNotNull().distinct().toTypedArray())
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(UIModel::class.qualifiedName!!).toList()
        val instructors = resolver.getSymbolsWithAnnotation(Instruction::class.qualifiedName!!).toList()
        val allPages = resolver.getSymbolsWithAnnotation(NovallesPage::class.qualifiedName!!).toList()
        val allCatalogues = resolver.getSymbolsWithAnnotation(NovallesCatalogue::class.qualifiedName!!).toList()
        val pages = allPages.firstOrNull() as? KSClassDeclaration?
        val catalogue = allCatalogues.firstOrNull() as? KSClassDeclaration?

        if (symbols.isEmpty() && instructors.isEmpty() && catalogue == null) return emptyList()

        checkSingle(allPages, NovallesPage::class.simpleName)
        checkSingle(allCatalogues, NovallesCatalogue::class.simpleName)

        if (pages != null) pageSeen = true
        if (strictCatalogue) checkStrictCatalogue(symbols + instructors, pages, catalogue)

        val validatedSymbols = symbols.map { it to it.validate() }
        val validatedInstructors = instructors.map { it to (it.validate() && it.instructedModel()?.validate() != false) }

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

    private fun checkSingle(annotated: List<KSAnnotated>, annotationName: String?) {
        val second = annotated.getOrNull(1) ?: return
        val names = annotated.joinToString { (it as? KSDeclaration)?.qualifiedName?.asString().toString() }
        ErrorHandler(logger).logError(second, "Only one class per module can be annotated with $annotationName, found: $names.")
    }

    private fun checkStrictCatalogue(annotated: List<KSAnnotated>, page: KSClassDeclaration?, catalogue: KSClassDeclaration?) {
        val errorHandler = ErrorHandler(logger)
        val first = annotated.firstOrNull()
        if (!pageSeen && first != null) {
            errorHandler.logError(
                first,
                "Strict catalogue mode is on, but this module has no NovallesPage, so its UI models could only be found with reflection."
            )
        }
        if (page != null && catalogue != null && cataloguePages(catalogue).none { it.declaration.qualifiedName?.asString() == page.qualifiedName?.asString() }) {
            errorHandler.logError(
                page,
                "Strict catalogue mode is on, but ${page.simpleName.getShortName()} is not listed in the pages of ${catalogue.simpleName.getShortName()}, so its UI models could only be found with reflection."
            )
        }
    }

    private fun cataloguePages(catalogue: KSClassDeclaration): List<KSType> {
        val pagesRaw = catalogue.findAnnotation(NovallesCatalogue::class)?.arguments?.first()?.value as ArrayList<*>
        return pagesRaw.filterIsInstance<KSType>()
    }

    private fun KSAnnotated.instructedModel(): KSClassDeclaration? {
        val instruction = (this as? KSClassDeclaration)?.findAnnotation(Instruction::class) ?: return null
        return (instruction.arguments.firstOrNull()?.value as? KSType)?.declaration?.closestClassDeclaration()
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
                val instructorKeys = catalogUIModels.keys.mapNotNull { catalogInstructors[it] }
                val classesList = (catalogUIModels.keys + instructorKeys).joinToString(
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
                for ((key, _) in catalogUIModels) {
                    val instructor = catalogInstructors[key] ?: continue
                    appendIn("$instructor::class -> ${catalogInspectors[key]}")
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
        val pages = cataloguePages(catalogue)

        //Strict mode: a miss throws, so the runtime never reaches the reflection fallback.
        val notInCatalogue = if (strictCatalogue) "throw IllegalStateException(\"Strict catalogue mode: \${clazz.qualifiedName} is not in any NovallesPage of this catalogue.\")" else "return null"
        val noHelper = if (strictCatalogue) " ?: throw IllegalStateException(\"Strict catalogue mode: \${clazz.qualifiedName} is not a UIModel.\")" else ""
        val noInspector = if (strictCatalogue) " ?: throw IllegalStateException(\"Strict catalogue mode: \${clazz.qualifiedName} has no Inspector, because its model has no Instruction.\")" else ""

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
                appendIn("else -> $notInCatalogue")
                appendDown("}")
                appendIn("return helper as UIModelHelper<T>?$noHelper")
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
                appendIn("else -> $notInCatalogue")
                appendDown("}")
                appendIn("return helper as Inspector<T, Any, Any>?$noInspector")
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
            if (model.findAnnotation(UIModel::class) == null) {
                errorHandler.logError(
                    classDeclaration,
                    "Instruction model ${model.simpleName.getShortName()} is not annotated with UIModel, so there are no payloads to handle."
                )
            }
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
            //The Instructor is a catalogue key too: R8 strips @Instruction, so it can't be mapped to its model at runtime.
            classDeclaration.qualifiedName?.asString()?.let { catalogInstructors[modelQualifiedName] = it }

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

            //Read from the model itself, not from processor state: the model may not be processed in this round.
            val fieldStates = model.uiFields()?.fields.orEmpty().map { it.toString() to it.type.resolve().isMarkedNullable }
            val payloads = fieldStates.map { (field, isNullable) -> Payloading("${field.capitalizeFirst()}Changed", isNullable) }
            val cachedFields = fieldStates.map { (field, isNullable) -> CachedField(field, isNullable) }

            checkHandlers(declarationFunctions, model, fieldStates.map { (field, _) -> field.capitalizeFirst() }.toSet())

            //Imports
            val listOfImports = listOfNotNull(
                viewHolder.qualifiedName?.asString(),
                if (payloads.isNotEmpty()) "$PACKAGE.${model.simpleName.getShortName()}Payloads.*" else null,
                classDeclaration.qualifiedName?.asString(),
                Inspector::class.qualifiedName,
                modelQualifiedName,
                "androidx.annotation.Keep",
                "com.flexeiprata.novalles.interfaces.Novalles",
                if (strictCatalogue) CatalogueOnly::class.qualifiedName else null
            )

            val text = buildString {
                add("package $PACKAGE")
                listOfImports.forEach {
                    add("import $it")
                }
                newLine(2)

                buildIn {
                    if (strictCatalogue) appendIn("@CatalogueOnly")
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

        private fun checkHandlers(functions: List<KSFunctionDeclaration>, model: KSClassDeclaration, fieldNames: Set<String>) {
            functions.forEach { function ->
                val functionName = function.simpleName.getShortName()
                val bindOn = function.findAnnotation(BindOn::class)
                val bindOnFields = function.findAnnotation(BindOnFields::class)
                val parameters = function.parameters
                val requiredParameters = parameters.filterNot { it.hasDefault }

                //BindOn: called with no argument, or with one Boolean argument
                if (bindOn != null && parameters.isNotEmpty() && (parameters.size > 1 || parameters.first().type.toString() != "Boolean")) {
                    logger.warn(
                        "$functionName is annotated with BindOn, but it should have no parameters or a single Boolean parameter, so Novalles never calls it.",
                        function
                    )
                }

                //BindOnFields and BindOnTag: always called with no arguments
                if ((bindOnFields != null || function.findAnnotation(BindOnTag::class) != null) && requiredParameters.isNotEmpty()) {
                    errorHandler.logError(
                        function,
                        "$functionName is annotated with ${if (bindOnFields != null) "BindOnFields" else "BindOnTag"}, so it should have no parameters."
                    )
                }

                val boundFields = when {
                    bindOn != null -> listOf(bindOn.arguments.first().value as String)
                    bindOnFields != null -> (bindOnFields.arguments.first().value as ArrayList<*>?)?.filterIsInstance<String>().orEmpty()
                    else -> emptyList()
                }
                boundFields.filterNot { it.capitalizeFirst() in fieldNames }.forEach { field ->
                    logger.warn(
                        "$functionName is bound to \"$field\", but ${model.simpleName.getShortName()} has no UI field with that name, so Novalles never calls it for that field. PrimaryTag and NonUIProperty fields never produce payloads.",
                        function
                    )
                }
            }
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

            val (key, fields) = classDeclaration.uiFields() ?: return
            val name = classDeclaration.simpleName.getShortName()
            val import = classDeclaration.qualifiedName?.asString()
            val payloadsBaseInterface = "BasePayload"

            //Generated names use the simple name only, so two models named alike would overwrite each other.
            catalogUIModels.keys.find { it != import && it.substringAfterLast('.') == name }?.let { other ->
                errorHandler.logError(
                    classDeclaration,
                    "UI model names should be unique within a module: $other and $import both generate ${name}UIHelper."
                )
            }

            val fileText = buildString {
                add("package $PACKAGE")
                newLine()

                val catalogueIndexName = classDeclaration.qualifiedName!!.asString()
                val catalogueValueName = PACKAGE + ".${name}UIHelper()"

                catalogUIModels[catalogueIndexName] = catalogueValueName
                classDeclaration.containingFile?.let(catalogUIModelFiles::add)

                //Field types are written fully qualified (see writeAsVariable), so they need no imports.
                add("import $import")
                add("import ${com.flexeiprata.novalles.interfaces.BasePayload::class.qualifiedName}")
                add("import androidx.annotation.Keep")
                add("import com.flexeiprata.novalles.interfaces.*")
                if (strictCatalogue) add("import ${CatalogueOnly::class.qualifiedName}")
                newLine()

                val payloadsName = "${name}Payloads"
                buildIn {
                    if (strictCatalogue) appendIn("@CatalogueOnly")
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


