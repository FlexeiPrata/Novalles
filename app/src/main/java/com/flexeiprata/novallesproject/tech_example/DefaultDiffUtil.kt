package com.flexeiprata.novallesproject.tech_example

import androidx.recyclerview.widget.DiffUtil
import com.flexeiprata.novalles.interfaces.Novalles
import com.flexeiprata.novalles.interfaces.UIModelHelper
import kotlin.reflect.KClass

class DefaultDiffUtil <T: BaseUiModel> (uiModel: KClass<T>) : DiffUtil.ItemCallback<BaseUiModel>() {

    private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAsFromCatalogue(MainCatalogue::class, uiModel)

    override fun areItemsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areItemsTheSame(newItem, uiModelHelper)
    }

    override fun areContentsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areContentTheSame(newItem, uiModelHelper)
    }

    override fun getChangePayload(oldItem: BaseUiModel, newItem: BaseUiModel): Any {
        return oldItem.changePayload(newItem, uiModelHelper)
    }

}