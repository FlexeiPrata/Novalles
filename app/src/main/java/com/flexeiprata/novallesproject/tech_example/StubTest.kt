package com.flexeiprata.novallesproject.tech_example

import androidx.recyclerview.widget.RecyclerView
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

@Instruction(StubModel::class)
@BindViewHolder(StubViewHolder::class)
class StubInstructor() : Instructor

class StubViewHolder(binding: ItemPictureBinding) : RecyclerView.ViewHolder(binding.root)