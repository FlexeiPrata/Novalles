package com.flexeiprata.novallesproject.tech_example

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.BindOnFields
import com.flexeiprata.novalles.annotations.BindOnTag
import com.flexeiprata.novalles.annotations.BindViewHolder
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.interfaces.Instructor
import com.flexeiprata.novalles.interfaces.Novalles.provideInspectorFromModelCatalogue
import com.flexeiprata.novallesproject.databinding.ItemPictureBinding

class PictureAdapter(private val onClick: (PictureUIModel) -> Unit) :
    ListAdapter<BaseUiModel, PictureAdapter.PictureViewHolder>(
        DefaultDiffUtil(PictureUIModel::class)
    ) {

    private val inspector = provideInspectorFromModelCatalogue(PictureUIModel::class)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        return PictureViewHolder(
            ItemPictureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(
        holder: PictureViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val model = currentList[position] as PictureUIModel
        val instructor = PictureInstructor(viewHolder = holder, model)
        inspector.inspectPayloads(payloads, instructor, viewHolder = holder) {
            inspector.bind(model, holder, instructor)
            holder.setOnClickActions(model, onClick)
        }
    }

    @Instruction(PictureUIModel::class)
    @BindViewHolder(PictureViewHolder::class)
    inner class PictureInstructor(
        private val viewHolder: PictureViewHolder,
        private val uiModel: PictureUIModel
    ) : Instructor {

        @BindOnFields(["title"])
        fun setTitleComplex() {
            val realDesc = "<b>${uiModel.title}</b> (${uiModel.tag})"
            viewHolder.setTitle(realDesc)
            viewHolder.setImage(uiModel.image)
        }

        @BindOnTag(PictureUIModel.Tags.SetNullLikes::class)
        fun setZeroLikes() {
            viewHolder.setLikes(0)
        }

        @BindOn("image")
        fun bindTestImage(isFromBind: Boolean) {
            if (isFromBind) {
                viewHolder.bindImage(uiModel.image)
            } else {
                viewHolder.setImage(uiModel.image)
            }
        }

    }

    inner class PictureViewHolder(private val binding: ItemPictureBinding) :
        ViewHolder(binding.root) {

        fun setImage(image: Int) {
            binding.image.animateColors(image)
        }

        fun bindImage(image: Int) {
            binding.image.setBackgroundColor(image)
        }

        fun bindLeftInLine(color: Int) {
            binding.colour.setBackgroundColor(color)
        }

        fun bindRightInLine(color: Int) {
            binding.colourSecond.setBackgroundColor(color)
        }

        fun setTitle(title: String) {
            binding.title.text = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        fun setDesc(desc: String) {
            binding.desc.text = desc
        }

        fun setLikes(likes: Int) {
            binding.likes.apply {
                animate()
                    .translationY(-height.toFloat())
                    .alpha(0.0f)
                    .setDuration(0)
                    .start()

                animate()
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(300)
                    .start()
                text = likes.toString()
            }
        }

        fun bindLikes(likes: Int) {
            binding.likes.text = likes.toString()
        }

        fun setOnClickActions(item: PictureUIModel, onClick: (PictureUIModel) -> Unit) {
            binding.delete.setOnClickListener {
                onClick(item)
            }
        }

        private fun View.animateColors(@ColorInt newColor: Int) {
            ObjectAnimator.ofObject(
                this,
                "backgroundColor",
                ArgbEvaluator(),
                (this.background as? ColorDrawable)?.color ?: run {
                    forceColor(newColor)
                    return
                },
                newColor
            ).apply {
                duration = 300
                start()
            }
        }

        private fun View.forceColor(color: Int) {
            background = ColorDrawable(color)
        }

    }

}