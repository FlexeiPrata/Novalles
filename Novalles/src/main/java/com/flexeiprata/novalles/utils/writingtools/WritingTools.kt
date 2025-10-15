package com.flexeiprata.novalles.utils.writingtools

import java.util.*

internal fun StringBuilder.buildIn(builder: StringBuilderTabulator.() -> Unit) {
    builder(StringBuilderTabulator(this))
}

internal class StringBuilderTabulator(private val builder: StringBuilder) {
    private var level = 0

    fun appendUp(text: String) {
        level++
        tabulation()
        builder.add(text)
    }

    fun appendDown(text: String) {
        level--
        tabulation()
        builder.add(text)
    }


    fun appendIn(text: String) {
        tabulation()
        builder.add(text)
    }

    fun configurableAppend(
        text: String,
        isTabulation: Boolean,
        levelChange: Int = 0,
        isEOL: Boolean = true
    ) {
        level += levelChange
        if (isTabulation) tabulation()
        builder.append(text)
        if (isEOL) builder.append("\n")
    }

    fun closeFunctions(minLevel: Int = 0) {
        while (level > minLevel) {
            appendDown("}")
        }
    }
    fun incrementLevel() {
        level++
    }

    private fun tabulation() {
        repeat(level) {
            builder.append("\t")
        }
    }

    val tabs get() = "\t".repeat(level)
}

internal fun StringBuilder.newLine(count: Int = 1) {
    repeat(count) {
        append("\n")
    }
}

internal fun StringBuilder.add(text: String) {
    append("$text\n")
}

internal fun String.capitalizeFirst(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

internal fun String.lowercaseFirst(): String {
    return this.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.ROOT) else it.toString() }
}