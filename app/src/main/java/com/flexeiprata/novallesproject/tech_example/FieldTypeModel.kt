package com.flexeiprata.novallesproject.tech_example

import androidx.recyclerview.widget.RecyclerView
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.PrimaryTag
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Instructor
import com.flexeiprata.novallesproject.databinding.ItemPictureBinding

//[Thing] is declared in another file, so its import must be traced across files.
@UIModel
data class FieldTypeModel(
    @PrimaryTag val id: String,
    val thing: Thing,
    val things: List<Thing>
)

@Instruction(FieldTypeModel::class)
@BindViewHolder(FieldTypeViewHolder::class)
class FieldTypeInstructor : Instructor

class FieldTypeViewHolder(private val binding: ItemPictureBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun setThing(thing: Thing) = Unit

    fun setThings(things: List<Thing>) = Unit
}
