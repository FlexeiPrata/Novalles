package com.flexeiprata.novalles.utils.writingtools

import java.util.*

fun StringBuilder.buildIn(builder: StringBuilderTabulator.() -> Unit) {
    builder(StringBuilderTabulator(this))
}

class StringBuilderTabulator(private val builder: StringBuilder) {
    var level = 0

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

    fun getTabs(): String {
        return StringBuilder().apply {
            repeat(level) {
                this.append("\t")
            }
        }.toString()
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
}

fun StringBuilder.newLine(count: Int = 1) {
    repeat(count) {
        append("\n")
    }
}

fun StringBuilder.add(text: String) {
    append("$text\n")
}

fun String.capitalizeFirst(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}