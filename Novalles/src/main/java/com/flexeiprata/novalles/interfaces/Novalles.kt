package com.flexeiprata.novalles.interfaces

import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.InspectorOfPayloads
import com.flexeiprata.novalles.interfaces.Novalles.ProviderOptions.UIModelInterface
import com.flexeiprata.novalles.utils.writingtools.tryNull
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import com.flexeiprata.novalles.annotations.*
import com.flexeiprata.novalles.interfaces.*

/**
 * Main class to interact with when you use payloads. Every annotated with [UIModel] data class can be used with it.
 * To use Novalles, you should:
 * 1. Annotate your UI Model (which you use in your Adapter) with [UIModel] annotation. You can also configure it with other annotations.
 * 2. Call [Novalles.provideUiInterfaceFor] or [Novalles.provideUiInterfaceForAs] in your diff util to check [UIModelHelper.areItemsTheSame] and [UIModelHelper.areContentsTheSame] and call [UIModelHelper.changePayloads].
 * 3. Create an Instructor class with [Instructor] interface and [Instruction] annotation, where [Instruction.model] is your UI Model. You can also annotate it with [AutoBindViewHolder]
 * 4. To retrieve your [Inspector] class, pass your instructor to [Novalles.provideInspectorFromInstructor] in your onBindViewHolder() and call [Inspector.inspectPayloads] on your payloads.
 *
 * @see [UIModel]
 * @see [Instructor]
 * @see [Instruction]
 */
object Novalles {

    /**
     * Provides an [UIModelHelper] from your [UIModel].
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceForAs]
     */
    fun <T: Any> provideUiInterfaceFor(clazz: KClass<T>): UIModelHelper<T> {
        val raw = fabricate(clazz, UIModelInterface)
        return raw.provide() ?: throw IllegalArgumentException("There is no UI interfaces")
    }

    /**
     * Provides an [UIModelHelper] from your [UIModel] and casts its generic to an [R] class. Use it, for example, if you use common interfaces for UI Models in your Diff Util.
     *
     * @return [UIModelHelper]
     * @see [Novalles.provideUiInterfaceFor]
     */
    fun <T: Any, R: Any> provideUiInterfaceForAs(clazz: KClass<T>): UIModelHelper<R> {
        val raw = fabricate(clazz, UIModelInterface)
        return raw.provide() as? UIModelHelper<R>? ?: throw IllegalArgumentException("There is no UI interfaces")
    }

    /**
     * Provides an [Inspector] from your [Instructor]. It should be annotated with [Instruction] and with option annotation [AutoBindViewHolder].
     */
    fun <R: Any> provideInspectorFromInstructor(inspector: Instructor, viewHolder: R): Inspector {
        val raw = fabricate(clazz = inspector::class, InspectorOfPayloads)
        return tryNull { raw.provide<Inspector>(inspector, viewHolder) } ?: throw IllegalArgumentException("There is no UI Inspectors")
    }

    private fun fabricate(clazz: KClass<*>, type: ProviderOptions): ProviderFactory<*> {
        val name = clazz.simpleName
        val className = when (type) {
            UIModelInterface -> "ksp.novalles.models.${name}UIHelper"
            InspectorOfPayloads -> "ksp.novalles.models.${name}Impl"
        }
        val from = Reflection.createKotlinClass(Class.forName(className))
        return ProviderFactory(from)
    }


    private enum class ProviderOptions {
        UIModelInterface, InspectorOfPayloads
    }

    private data class ProviderFactory <T: Any>(
        val clazz: KClass<T>,
    ) {
        inline fun <reified R> provide(vararg args: Any): R? {
            val contractor = clazz.primaryConstructor ?: return null
            val obj = contractor.call(*args)
            return if (obj is R) obj else null
        }
    }

}