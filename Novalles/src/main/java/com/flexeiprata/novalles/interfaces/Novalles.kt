package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.CatalogueOnly
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.NovallesCatalogue
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.InspectorOfPayloadsDirect
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.InspectorOfPayloadsIndirect
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.UIModelInterface
import com.flexeiprata.novalles.processor.CATALOGUES_PACKAGE
import com.flexeiprata.novalles.processor.PACKAGE
import com.flexeiprata.novalles.utils.writingtools.tryNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Main class to interact with when you use payloads. Every annotated with [UIModel] data class can be used with it.
 * To use Novalles, you should:
 * 1. Annotate your UI Model (which you use in your Adapter) with [UIModel] annotation. You can also configure it with other annotations.
 * 2. Call [Novalles.provideUiInterfaceForAsFromCatalogue] in your diff util to check [UIModelHelper.areItemsTheSame] and [UIModelHelper.areContentsTheSame] and call [UIModelHelper.changePayloads].
 * 3. Create an Instructor class with [Instructor] interface and [Instruction] annotation, where [Instruction.model] is your UI Model. You should also annotate it with [BindViewHolder]
 * 4. To retrieve your [Inspector] class, pass your UI Model or instructor to [Novalles.provideInspectorFromModelCatalogue] or [Novalles.provideInspectorFromInstructorCatalogue]
 * in your onBindViewHolder() and call [Inspector.inspectPayloads] on your payloads.
 *
 * @see [UIModel]
 * @see [Instructor]
 * @see [Instruction]
 * @see [NovallesCatalogue]
 */
object Novalles {

    private val cache = mutableMapOf<String, UIModelHelper<Any>>()
    private val catalogue: Catalogue? by lazy { createCatalogue() }
    private val reportedMisses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Provides an [UIModelHelper] from your [UIModel].
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceForAsFromCatalogue]
     */
    @Deprecated(
        "Uses reflection keyed on class names, which R8 can break. Use the catalogue instead.",
        ReplaceWith("provideUiInterfaceForAsFromCatalogue<T, T>(clazz)"),
        DeprecationLevel.ERROR
    )
    fun <T : Any> provideUiInterfaceFor(clazz: KClass<T>): UIModelHelper<T> {
        return uiInterfaceByReflection(clazz)
    }

    /**
     * Provides an [UIModelHelper] from your [UIModel] and casts its generic to an [R] class. Use it, for example, if you use common interfaces for UI Models in your Diff Util.
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceForAsFromCatalogue]
     */
    @Deprecated(
        "Uses reflection keyed on class names, which R8 can break. Use the catalogue instead.",
        ReplaceWith("provideUiInterfaceForAsFromCatalogue<T, R>(clazz)"),
        DeprecationLevel.ERROR
    )
    fun <T : Any, R : Any> provideUiInterfaceForAs(clazz: KClass<T>): UIModelHelper<R> {
        return uiInterfaceByReflection(clazz)
    }

    /**
     * Provides an [UIModelHelper] from your [UIModel] and casts its generic to an [R] class.
     * You should have one [NovallesCatalogue] class and provide it in the [catalogue] field.
     */
    fun <T : Any, R : Any> provideUiInterfaceForAsFromCatalogue(clazz: KClass<T>): UIModelHelper<R> {
        return catalogue?.provideUiModel(clazz) ?: run {
            reportMiss("UI Interface ${clazz.simpleName} cannot be created from catalogue.")
            uiInterfaceByReflection(clazz)
        }
    }

    /**
     * Provides an [Inspector] from your [UIModel]. It should be linked with only one [Instructor] via [Instruction].
     * You should have one [NovallesCatalogue] class and provide it in the [catalogue] field.
     */
    fun provideInspectorFromModelCatalogue(uiModel: KClass<out Any>): Inspector<Instructor, Any, Any> {
        return catalogue?.provideInspector(uiModel) ?: run {
            reportMiss("model ${uiModel.simpleName} cannot be created from catalogue.")
            inspectorFromModelByReflection(uiModel)
        }
    }

    /**
     * Provides an [Inspector] from your [Instructor]. It should be annotated with [Instruction] and with option annotation [BindViewHolder].
     * You should have one [NovallesCatalogue] class and provide it in the [catalogue] field.
     */
    fun <R : Instructor> provideInspectorFromInstructorCatalogue(instructor: KClass<R>): Inspector<R, Any, Any> {
        return catalogue?.provideInspector(instructor) ?: run {
            reportMiss("instructor ${instructor.simpleName} cannot be created from catalogue.")
            inspectorFromInstructorByReflection(instructor)
        }
    }

    /**
     * Experimental.
     *
     * Do the same, as [provideUiInterfaceForAs], but if instance of [UIModelHelper] was already created, simply get it from RAM.
     */
    @Deprecated("Cached instances are replaced with Novalles' catalogues", level = DeprecationLevel.ERROR)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, R : Any> provideCachedUiInterfaceForAs(clazz: KClass<T>): UIModelHelper<R> {
        return (cache[clazz.qualifiedName] as UIModelHelper<R>?) ?: (uiInterfaceByReflection<R>(clazz).also {
            cache[clazz.qualifiedName!!] = it as UIModelHelper<Any>
        })
    }

    @Deprecated("Cached instances are replaced with Novalles' catalogues", level = DeprecationLevel.ERROR)
    fun clearCache() {
        cache.clear()
    }

    /**
     * Provides an [Inspector] from your [Instructor]. It should be annotated with [Instruction] and with option annotation [BindViewHolder].
     *
     * @see [Novalles.provideInspectorFromInstructorCatalogue]
     */
    @Deprecated(
        "Uses reflection keyed on class names, which R8 can break. Use the catalogue instead.",
        ReplaceWith("provideInspectorFromInstructorCatalogue(instructor)"),
        DeprecationLevel.ERROR
    )
    fun <R : Instructor> provideInspectorFromInstructor(instructor: KClass<R>): Inspector<R, Any, Any> {
        return inspectorFromInstructorByReflection(instructor)
    }

    /**
     * Provides an [Inspector] from your [UIModel]. It should be linked with only one [Instructor] via [Instruction].
     *
     * @see [Novalles.provideInspectorFromModelCatalogue]
     */
    @Deprecated(
        "Uses reflection keyed on class names, which R8 can break. Use the catalogue instead.",
        ReplaceWith("provideInspectorFromModelCatalogue(uiModel)"),
        DeprecationLevel.ERROR
    )
    fun provideInspectorFromUiModelRaw(uiModel: KClass<out Any>): Inspector<Instructor, Any, Any> {
        return inspectorFromModelByReflection(uiModel)
    }

    /**
     * Provides an [Inspector] from your [UIModel]. It should be linked with only one [Instructor] via [Instruction].
     *
     * @see [Novalles.provideInspectorFromModelCatalogue]
     */
    @Deprecated(
        "Uses reflection keyed on class names, which R8 can break. Use the catalogue instead.",
        ReplaceWith("provideInspectorFromModelCatalogue(M::class)"),
        DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    inline fun <reified M : Any> provideInspectorFromUiModel(): Inspector<Instructor, Any, M> {
        return inspectorFromModelByReflection(M::class) as Inspector<Instructor, Any, M>
    }

    /**
     * This function is used internally by Novalles to extract payload from Any? from OnBindViewHolder.
     * RecyclerView merges payloads of updates that arrive before one layout pass, so every nested list is flattened.
     *
     * @see [Inspector.inspectPayloads]
     */
    fun extractPayload(from: Any?): List<Any> {
        return when (from) {
            is List<*> -> from.flatMap { if (it is List<*>) it.filterNotNull() else listOfNotNull(it) }
            is Any -> listOf(from)
            else -> emptyList()
        }
    }

    //Reflection path: the deprecated provide functions and the catalogue fallback share it.
    private fun <R : Any> uiInterfaceByReflection(clazz: KClass<*>): UIModelHelper<R> {
        return instantiate(fabricate(clazz, UIModelInterface)) ?: throw IllegalArgumentException("There is no UI interfaces. If it happened on the release build, check if you keep your UIModels' names.")
    }

    private fun <R : Instructor> inspectorFromInstructorByReflection(instructor: KClass<R>): Inspector<R, Any, Any> {
        val raw = fabricate(clazz = instructor, InspectorOfPayloadsIndirect)
        return tryNull {
            instantiate<Inspector<R, Any, Any>>(raw)
        } ?: run {
            throw IllegalArgumentException("There is no UI Inspectors")
        }
    }

    @PublishedApi
    internal fun inspectorFromModelByReflection(uiModel: KClass<out Any>): Inspector<Instructor, Any, Any> {
        val raw = fabricate(clazz = uiModel, InspectorOfPayloadsDirect)
        return tryNull {
            instantiate<Inspector<Instructor, Any, Any>>(raw)
        } ?: run {
            throw IllegalArgumentException("There is no UI Inspectors")
        }
    }

    private fun fabricate(clazz: KClass<*>, type: ProviderOptions): Class<*> {
        val name = clazz.simpleName
        val className = when (type) {
            UIModelInterface -> "$PACKAGE.${name}UIHelper"
            InspectorOfPayloadsIndirect -> {
                val uiModelName = clazz.java.getAnnotation(Instruction::class.java)?.model
                    ?: throw IllegalArgumentException("${clazz.simpleName} is not annotated with Instruction.")
                "$PACKAGE.${uiModelName.simpleName}Inspector"
            }

            InspectorOfPayloadsDirect -> "$PACKAGE.${name}Inspector"
        }
        val found = Class.forName(className)
        if (found.isAnnotationPresent(CatalogueOnly::class.java)) {
            throw IllegalStateException("Strict catalogue mode: ${clazz.simpleName} was not found in the catalogue, and Novalles won't create ${found.simpleName} with reflection. Check that the NovallesPage of its module is listed in your NovallesCatalogue.")
        }
        return found
    }

    private inline fun <reified R> instantiate(from: Class<*>): R? {
        return from.getDeclaredConstructor().newInstance() as? R
    }

    private fun createCatalogue(): Catalogue? {
        return tryNull {
            instantiate<Catalogue>(Class.forName("$CATALOGUES_PACKAGE.NovallesCatalogue"))
        }
    }

    private fun reportMiss(message: String) {
        if (reportedMisses.add(message)) println("Novalles warning :: $message")
    }

    private enum class ProviderOptions {
        UIModelInterface, InspectorOfPayloadsIndirect, InspectorOfPayloadsDirect
    }

}
