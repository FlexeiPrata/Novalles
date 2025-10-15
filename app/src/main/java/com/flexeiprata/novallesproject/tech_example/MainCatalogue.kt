package com.flexeiprata.novallesproject.tech_example

import com.flexeiprata.novalles.annotations.NovallesCatalogue
import com.flexeiprata.novalles.annotations.NovallesPage

//Main UI module
@NovallesCatalogue(
    pages = [MainPage::class]
)
object MainCatalogue


//One page for each module
@NovallesPage
object MainPage

