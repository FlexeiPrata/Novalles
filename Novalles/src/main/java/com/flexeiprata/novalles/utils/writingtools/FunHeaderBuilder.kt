package com.flexeiprata.novalles.utils.writingtools

fun funHeaderBuilder(
    extension: String? = null,

    name: String,
    returnType: String,
    vararg args: String
): String {
    return StringBuilder().apply {
        append("fun ")
        extension?.let { append("$extension.") }
        append(name)
        append(
            args.joinToString(
                separator = ", ",
                prefix = "(",
                postfix = ") "
            )
        )
        append(": $returnType")
        append(" {")
    }.toString()
}

fun funHeaderBuilder(
    name: String,
    isOverridden: Boolean = false,
    extension: String? = null,
    modifier: String? = null,
    returnType: String? = null,
    tabs: String = "",
    genericString: String = "",
    args: List<String>
): String {
    return StringBuilder().apply {
        val sign = if (args.size > 2) "\n" else ""
        if (isOverridden) append("override ")
        modifier?.let {
            append("$it ")
        }
        append("fun ")
        if (genericString.isNotBlank()) {
            append("$genericString ")
        }
        extension?.let { append("$extension.") }
        append(name)
        append(
            args.joinToString(
                separator = ",$sign ",
                prefix = "($sign",
                postfix = "$sign${tabs.dropLast(1)})"
            ) { if (args.size > 2) "$tabs$it" else it }
        )
        returnType?.let {
            append(": $returnType")
        }
        append(" {")
    }.toString()
}

fun dataClassConstructor(
    name: String,
    parent: String? = null,
    vararg data: String
): String {
    return StringBuilder().apply {
        append("data class $name")
        append(
            data.joinToString(
                prefix = "(",
                separator = ", ",
                postfix = ")"
            ) { "val $it" }
        )
        parent?.let {
            append(": $parent")
        }
    }.toString()
}