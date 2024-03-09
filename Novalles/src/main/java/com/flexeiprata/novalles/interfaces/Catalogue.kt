package com.flexeiprata.novalles.interfaces

interface Catalogue {

    fun <T> provideUiModel(classQualifiedName: String): UIModelHelper<T>

    fun <T : Instructor> provideInspector(classQualifiedName: String): Inspector<T, Any, Any>

}