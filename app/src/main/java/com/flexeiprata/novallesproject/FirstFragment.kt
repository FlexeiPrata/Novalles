package com.flexeiprata.novallesproject

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.flexeiprata.novallesproject.databinding.FragmentFirstBinding
import com.flexeiprata.novallesproject.tech_example.BaseUiModel
import com.flexeiprata.novallesproject.tech_example.PictureAdapter
import com.flexeiprata.novallesproject.tech_example.PictureUIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val randomizer = Random()

    private val data = run {
        (0..50).map {
            PictureUIModel(
                tag = it.toString(),
                image = generateRandomColor(randomizer),
                title = getRandomString(10),
                desc = getRandomString(40),
                imageCode = "$it".repeat(10),
                likes = randomizer.nextInt(501) + 500
            )
        }
    }

    private val dataChannel = MutableStateFlow<List<BaseUiModel>>(data)

    @OptIn(ExperimentalTime::class)
    private val novallesAdapter by lazy {
        val (value, duration) = measureTimedValue {
            PictureAdapter(this::processClick)
        }
        Log.d("Time", duration.toString())
        value
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dataChannel.emitIn(this, data)
        binding.mainRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = novallesAdapter
        }
        lifecycleScope.launch {
            dataChannel.collectLatest {
                novallesAdapter.submitList(it)
            }
        }

        //RandomLikes
        binding.upvote.setOnClickListener {
            val newList = novallesAdapter.currentList.map {
                val model = it as PictureUIModel
                model.copy(
                    likes = if (Math.random() > 0.50) model.likes + randomizer.nextInt(15) else model.likes
                )
            }
            dataChannel.tryEmit(newList)
        }

        //Colors
        binding.colors.setOnClickListener {
            val newList = novallesAdapter.currentList.map {
                val model = it as PictureUIModel
                model.copy(
                    image = generateRandomColor(randomizer)
                )
            }
            dataChannel.tryEmit(newList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun processClick(item: PictureUIModel) {
        dataChannel.emitIn(this, novallesAdapter.castList<PictureUIModel>().minus(item))
    }

    private fun generateRandomColor(randomizer: Random) =
        Color.argb(255, randomizer.nextInt(256), randomizer.nextInt(256), randomizer.nextInt(256))

    private fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun <T> MutableStateFlow<T>.emitIn(fragment: Fragment, value: T) {
        fragment.lifecycleScope.launch(Dispatchers.IO) {
            emit(value)
        }
    }

    private inline fun <reified T : BaseUiModel> ListAdapter<BaseUiModel, *>.castList(): List<T> {
        return currentList.filterIsInstance<T>()
    }
}