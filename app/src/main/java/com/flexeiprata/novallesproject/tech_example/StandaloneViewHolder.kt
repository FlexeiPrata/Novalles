package com.flexeiprata.novallesproject.tech_example

import androidx.recyclerview.widget.RecyclerView
import com.flexeiprata.novallesproject.databinding.ItemPictureBinding

/**
 * Lives in its own file on purpose: the Instructor that binds to it is in [StandaloneModel]'s
 * file, so renaming a setter here must still regenerate that model's Inspector.
 */
class StandaloneViewHolder(private val binding: ItemPictureBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun setLabel(label: String) = Unit
}
