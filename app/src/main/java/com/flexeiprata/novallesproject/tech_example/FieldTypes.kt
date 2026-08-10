package com.flexeiprata.novallesproject.tech_example

/**
 * A user-declared field type in its own file. Every other model uses only stdlib types, so
 * without this the generated import map is never exercised.
 */
data class Thing(
    val name: String,
    val weight: Int
)
