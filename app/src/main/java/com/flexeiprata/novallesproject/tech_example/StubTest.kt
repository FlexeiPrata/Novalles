package com.flexeiprata.novallesproject.tech_example

import androidx.recyclerview.widget.RecyclerView
import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.BindOnTag
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.PrimaryTag
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Instructor
import com.flexeiprata.novallesproject.databinding.ItemPictureBinding


@UIModel
data class StubModel(
    @PrimaryTag val tag: String
)

//This model has no changeable fields, so its tag handler is the only thing to generate.
@Instruction(StubModel::class)
@BindViewHolder(StubViewHolder::class)
class StubInstructor : Instructor {

    @BindOnTag(StubTags.Refresh::class)
    fun onRefresh() = Unit
}

class StubViewHolder(binding: ItemPictureBinding) : RecyclerView.ViewHolder(binding.root)

sealed interface StubTags {
    object Refresh
}

//Covers generic and star-projected fields in payload generation.
@UIModel
data class GenericStubModel(
    @PrimaryTag val id: String,
    val items: List<*>,
    val tags: List<String>,
    val note: String?
)

@Instruction(GenericStubModel::class)
@BindViewHolder(GenericStubViewHolder::class)
class GenericStubInstructor : Instructor {

    //The annotation before BindOn must not be mistaken for it.
    @Suppress("unused")
    @BindOn("items")
    fun onItems() = Unit
}

class GenericStubViewHolder(private val binding: ItemPictureBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun setTags(tags: List<String>) = Unit

    fun setNote(note: String?) = Unit
}
