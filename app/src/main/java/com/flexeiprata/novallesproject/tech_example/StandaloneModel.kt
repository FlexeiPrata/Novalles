package com.flexeiprata.novallesproject.tech_example

import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.PrimaryTag
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Instructor

@UIModel
data class StandaloneModel(
    @PrimaryTag val id: String,
    val label: String
)

//The bound ViewHolder is declared in another file, see [StandaloneViewHolder].
@Instruction(StandaloneModel::class)
@BindViewHolder(StandaloneViewHolder::class)
class StandaloneInstructor : Instructor
