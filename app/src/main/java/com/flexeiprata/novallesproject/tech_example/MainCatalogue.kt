package com.flexeiprata.novallesproject.tech_example

import com.flexeiprata.novalles.annotations.NovallesCatalogue

//Main UI module. The page it references lives in its own file, see [MainPage].
@NovallesCatalogue(
    pages = [MainPage::class]
)
object MainCatalogue
