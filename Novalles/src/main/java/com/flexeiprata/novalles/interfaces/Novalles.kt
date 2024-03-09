package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.InspectorOfPayloadsDirect
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.InspectorOfPayloadsIndirect
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.UIModelInterface
import com.flexeiprata.novalles.utils.writingtools.tryNull
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Main class to interact with when you use payloads. Every annotated with [UIModel] data class can be used with it.
 * To use Novalles, you should:
 * 1. Annotate your UI Model (which you use in your Adapter) with [UIModel] annotation. You can also configure it with other annotations.
 * 2. Call [Novalles.provideUiInterfaceFor], [Novalles.provideUiInterfaceForAs] or [Novalles.provideUiInterfaceForAsFromCatalogue] in your diff util to check [UIModelHelper.areItemsTheSame] and [UIModelHelper.areContentsTheSame] and call [UIModelHelper.changePayloads].
 * 3. Create an Instructor class with [Instructor] interface and [Instruction] annotation, where [Instruction.model] is your UI Model. You should also annotate it with [BindViewHolder]
 * 4. To retrieve your [Inspector] class, pass your instructor to one of these functions: [Novalles.provideInspectorFromInstructor], [Novalles.provideInspectorFromModelCatalogue] or [Novalles.provideInspectorFromInstructorCatalogue]
 * in your onBindViewHolder() and call [Inspector.inspectPayloads] on your payloads.
 *
 * @see [UIModel]
 * @see [Instructor]
 * @see [Instruction]
 */
object Novalles {

    private val cache = mutableMapOf<String, UIModelHelper<Any>>()
    private val catalogue by lazy { createCatalogue() }

    /**
     * Provides an [UIModelHelper] from your [UIModel].
     * Note: it might be deprecated in the future and replaced with [provideUiInterfaceForAsFromCatalogue].
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceForAs]
     */
    fun <T : Any> provideUiInterfaceFor(clazz: KClass<T>): UIModelHelper<T> {
        val raw = fabricate(clazz, UIModelInterface)
        return raw.provide() ?: throw IllegalArgumentException("There is no UI interfaces. If it happened on the release build, check if you keep your UIModels' names.")
    }

    /**
     * Provides an [UIModelHelper] from your [UIModel] and casts its generic to an [R] class. Use it, for example, if you use common interfaces for UI Models in your Diff Util.
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceFor]
     */
    fun <T : Any, R : Any> provideUiInterfaceForAs(clazz: KClass<T>): UIModelHelper<R> {
        val raw = fabricate(clazz, UIModelInterface)
        return raw.provide() as? UIModelHelper<R>? ?: throw IllegalArgumentException("There is no UI interfaces. If it happened on the release build, check if you keep your UIModels' names.")
    }

    fun <T : Any, R : Any> provideUiInterfaceForAsFromCatalogue(clazz: KClass<T>): UIModelHelper<R> {
        return catalogue.provideUiModel(clazz.qualifiedName!!)
    }

    fun provideInspectorFromModelCatalogue(uiModel: KClass<out Any>): Inspector<Instructor, Any, Any> {
        return catalogue.provideInspector(uiModel.qualifiedName!!)
    }

    fun provideInspectorFromInstructorCatalogue(instructor: KClass<Instructor>): Inspector<Instructor, Any, Any> {
        val model = instructor.findAnnotation<Instruction>()?.model ?: throw Exception("Invalid instruction class")
        return catalogue.provideInspector(model.qualifiedName!!)
    }

    /**
     * Experimental.
     *
     * Do the same, as [provideUiInterfaceForAs], but if instance of [UIModelHelper] was already created, simply get it from RAM.
     */
    @Deprecated("Cached instances are replaced with Novalles' catalogues")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, R : Any> provideCachedUiInterfaceForAs(clazz: KClass<T>): UIModelHelper<R> {
        return (cache[clazz.qualifiedName] as UIModelHelper<R>?) ?: (provideUiInterfaceForAs<T, R>(clazz).also {
            cache[clazz.qualifiedName!!] = it as UIModelHelper<Any>
        })
    }

    @Deprecated("Cached instances are replaced with Novalles' catalogues")
    fun clearCache() {
        cache.clear()
    }

    /**
     * Provides an [Inspector] from your [Instructor]. It should be annotated with [Instruction] and with option annotation [BindViewHolder].
     *
     * Note: it might be deprecated in the future and replaced with [provideInspectorFromInstructorCatalogue].
     */
    fun <R : Instructor> provideInspectorFromInstructor(instructor: KClass<R>): Inspector<R, Any, Any> {
        val raw = fabricate(clazz = instructor, InspectorOfPayloadsIndirect)
        return tryNull {
            raw.provide<Inspector<R, Any, Any>>()
        } ?: run {
            throw IllegalArgumentException("There is no UI Inspectors")
        }
    }

    /**
     * Provides an [Inspector] from your [UIModel]. It should be linked with only one [Instructor] via [Instruction].
     *
     * Note: it might be deprecated in the future and replaced with [provideInspectorFromModelCatalogue].
     */
    fun provideInspectorFromUiModelRaw(uiModel: KClass<out Any>): Inspector<Instructor, Any, Any> {
        val raw = fabricate(clazz = uiModel, InspectorOfPayloadsDirect)
        return tryNull {
            raw.provide<Inspector<Instructor, Any, Any>>()
        } ?: run {
            throw IllegalArgumentException("There is no UI Inspectors")
        }
    }

    /**
     * Provides an [Inspector] from your [UIModel]. It should be linked with only one [Instructor] via [Instruction].
     *
     * Note: it might be deprecated in the future and replaced with [provideInspectorFromModelCatalogue].
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified M : Any> provideInspectorFromUiModel(): Inspector<Instructor, Any, M> {
        return provideInspectorFromUiModelRaw(M::class) as Inspector<Instructor, Any, M>
    }

    /**
     * This function is used internally by Novalles to extract payload from Any? from OnBindViewHolder.
     *
     * @see [Inspector.inspectPayloads]
     */
    fun extractPayload(from: Any?): List<Any> {
        return when (from) {
            is List<*> -> when {
                from.isEmpty() -> emptyList()
                from.first() is List<*> -> (from.first() as List<*>).map { it as Any }
                else -> from.map { it as Any }
            }

            is Any -> listOf(from)
            else -> emptyList()
        }
    }

    private fun fabricate(clazz: KClass<*>, type: ProviderOptions): ProviderFactory<*> {
        val name = clazz.simpleName
        val className = when (type) {
            UIModelInterface -> "ksp.novalles.models.${name}UIHelper"
            InspectorOfPayloadsIndirect -> {
                val uiModelName = (clazz.annotations.find { it is Instruction } as Instruction).model
                "ksp.novalles.models.${uiModelName.simpleName}Inspector"
            }

            InspectorOfPayloadsDirect -> "ksp.novalles.models.${name}Inspector"
        }
        val from = Reflection.createKotlinClass(Class.forName(className))
        return ProviderFactory(from)
    }

    private fun createCatalogue(): Catalogue {
        val from = Reflection.createKotlinClass(Class.forName("ksp.novalles.catalogues.NovallesCatalogue"))
        return ProviderFactory(from).provide<Catalogue>() ?: throw IllegalArgumentException("Catalogue is not created, report a bug")
    }

    private enum class ProviderOptions {
        UIModelInterface, InspectorOfPayloadsIndirect, InspectorOfPayloadsDirect
    }

    private data class ProviderFactory<T : Any>(
        val clazz: KClass<T>,
    ) {
        inline fun <reified R> provide(vararg args: Any): R? {
            val constructor = clazz.primaryConstructor ?: return null
            val obj = constructor.call(*args)
            return if (obj is R) obj else null
        }
    }

}