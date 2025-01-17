package com.example.testmodule

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.annotations.PrimaryTag
import com.flexeiprata.novalles.annotations.UIModel
import com.flexeiprata.novalles.interfaces.Instructor


@UIModel
data class StubModel2(
    @PrimaryTag val tag: String
)

@Instruction(StubModel2::class)
@BindViewHolder(StubViewHolder::class)
class Stub2Instructor() : Instructor

class StubViewHolder(group: ViewGroup) : RecyclerView.ViewHolder(group)