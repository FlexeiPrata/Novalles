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
import com.flexeiprata.novalles.annotations.AutoBindViewHolder
import com.flexeiprata.novalles.annotations.BindOn
import com.flexeiprata.novalles.annotations.Instruction
import com.flexeiprata.novalles.interfaces.Instructor
import com.flexeiprata.novalles.interfaces.Novalles
import com.flexeiprata.novallesproject.databinding.ItemPictureBinding

class PictureAdapter(private val onClick: (PictureUIModel) -> Unit) :
    ListAdapter<BaseUiModel, PictureAdapter.PictureViewHolder>(
        DefaultDiffUtil(PictureUIModel::class)
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        return PictureViewHolder(
            ItemPictureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(
        holder: PictureViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val model = currentList[position] as PictureUIModel
        val inspector = Novalles.provideInspectorFromInstructor(
            inspector = PictureInstructor(
                viewHolder = holder,
                uiModel = currentList[position] as PictureUIModel
            ),
            viewHolder = holder
        )
        val picturePayloads = payloads.firstOrNull() as? List<Any>
        if (picturePayloads.isNullOrEmpty()) {
            holder.bind(model)
        } else {
            inspector.inspectPayloads(picturePayloads)
        }

        holder.setOnClickActions(model, onClick)
    }

    @Instruction(PictureUIModel::class)
    @AutoBindViewHolder(PictureViewHolder::class)
    inner class PictureInstructor(
        private val viewHolder: PictureViewHolder,
        private val uiModel: PictureUIModel
    ) : Instructor {

        @BindOn("title")
        fun setTitleComplex(title: String) {
            val realDesc = "<b>$title</b> (${uiModel.tag})"
            viewHolder.setTitle(realDesc)
        }

    }

    inner class PictureViewHolder(private val binding: ItemPictureBinding) :
        ViewHolder(binding.root) {

        fun bind(item: PictureUIModel) {
            setImage(item.image)
            setLeftInLine(item.line.left)
            setRightInLine(item.line.right)
            setTitle("<b>${item.title}</b> (${item.tag})")
            setDesc(item.desc)
            setLikes(item.likes)
        }

        fun setImage(image: Int) {
            binding.image.animateColors(image)
        }

        fun setLeftInLine(color: Int) {
            binding.colour.animateColors(color)
        }

        fun setRightInLine(color: Int) {
            binding.colourSecond.animateColors(color)
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