package com.flexeiprata.novallesproject.tech_example

import android.util.Log
import androidx.recyclerview.widget.DiffUtil
import com.flexeiprata.novalles.interfaces.Novalles
import com.flexeiprata.novalles.interfaces.UIModelHelper
import kotlin.reflect.KClass

class DefaultDiffUtil <T: BaseUiModel> (uiModel: KClass<T>) : DiffUtil.ItemCallback<BaseUiModel>() {

    private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAs(uiModel)

    override fun areItemsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areItemsTheSame(newItem, uiModelHelper).also {
            Log.d("Payloads", "areItemsTheSame = $it")
        }
    }

    override fun areContentsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areContentTheSame(newItem, uiModelHelper).also {
            Log.d("Payloads", "areContentTheSame = $it")
        }
    }

    override fun getChangePayload(oldItem: BaseUiModel, newItem: BaseUiModel): Any {
        return oldItem.changePayload(newItem, uiModelHelper).also {
            Log.d("Payloads", "payloads = $it")
        }
    }

}