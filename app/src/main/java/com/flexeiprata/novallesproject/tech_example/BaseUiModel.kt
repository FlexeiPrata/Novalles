package com.flexeiprata.novallesproject.tech_example

import com.flexeiprata.novalles.interfaces.UIModelHelper

interface BaseUiModel {

    fun areItemsTheSame (other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): Boolean
    fun areContentTheSame(other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): Boolean
    fun changePayload(other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): List<Any>

}