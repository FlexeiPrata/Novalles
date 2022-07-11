package com.flexeiprata.novallesproject.tech_example

import com.flexeiprata.novalles.annotations.Decompose
import com.flexeiprata.novalles.annotations.NonUIProperty
import com.flexeiprata.novalles.annotations.PrimaryTag
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.UIModelHelper

@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    val image: Int,
    @Decompose val line: ColorPair,
    @NonUIProperty val imageCode: String,
    val title: String,
    val desc: String,
    val likes: Int
): BaseUiModel {

    override fun areItemsTheSame(other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): Boolean {
        return helper.areItemsTheSame(this, other)
    }

    override fun areContentTheSame(other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): Boolean {
        return helper.areContentsTheSame(this, other)
    }

    override fun changePayload(other: BaseUiModel, helper: UIModelHelper<BaseUiModel>): List<Any> {
        return helper.changePayloads(this, other)
    }

}

data class ColorPair(
    val left: Int,
    val right: Int
)