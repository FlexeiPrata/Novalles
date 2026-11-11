package com.flexeiprata.novalles.utils

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Key and payload fields of a UI model. Both visitors read it, so the UIHelper and the Inspector cannot disagree.
 */
internal fun KSClassDeclaration.uiFields(): UIFields? {
    val constructor = primaryConstructor ?: return null

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

    val key = primaryTag ?: constructor.parameters.firstOrNull() ?: return null
    return UIFields(key, constructor.parameters.filterNot { it == key || it in notUI })
}

internal enum class KUIAnnotations {
    UIModel, PrimaryTag, NonUIProperty
}
