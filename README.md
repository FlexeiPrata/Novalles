# Novalles

*Library to simplify and speed up the creation and work with adapters with payload.*

[![License](https://img.shields.io/badge/License%20-Apache%202-337ab7.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![](https://jitpack.io/v/FlexeiPrata/Novalles.svg)](https://jitpack.io/#FlexeiPrata/Novalles)

## How to use

1. Annotate your UI model with **UIModel** Annotation.

````kotlin
@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    val image: Int,
    @Decompose val line: ColorPair,
    @NonUIProperty val imageCode: String,
    val title: String,
    val desc: String,
    val likes: Int
) : BaseUiModel
````

* You can use **PrimaryTag** annotation to define property to be used in are items the same comparison.
* Use **NonUIProperty** annotation to define property that will not be used in any comparisons.
* Use **Decompose** annotation to tell Novalles compare each property of decomposed value separately. See _Decompose_
  section for more details.

2. Pass an instance of **_UIModelHelper_** in your DiffUtil using **provideUiInterfaceFor** or
   **provideUiInterfaceForAs** functions.

````kotlin
private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAs(uiModel)
````

3. Call relevant functions of _UIModelHelper_ in your DiffUtil. This example uses diffUtil based on common interface.

````kotlin
class DefaultDiffUtil<T : BaseUiModel>(uiModel: KClass<T>) : DiffUtil.ItemCallback<BaseUiModel>() {

    private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAs(uiModel)

    override fun areItemsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areItemsTheSame(newItem, uiModelHelper)
    }

    override fun areContentsTheSame(oldItem: BaseUiModel, newItem: BaseUiModel): Boolean {
        return oldItem.areContentTheSame(newItem, uiModelHelper)
    }

    override fun getChangePayload(oldItem: BaseUiModel, newItem: BaseUiModel): Any {
        return oldItem.changePayload(newItem, uiModelHelper)
    }
}
````

4. Create a class, that extends **Instructor** interface. Annotate it with **Instruction** annotation, pass your _UI
   Model_ class as the annotation argument. You can also annotate it with **AutoBindViewHolder** _(See corresponding
   section for more details)_. In this class you should create functions, that will be called on a value change. Use **
   BindOn** annotation to tell Novalles which function should be called when a value has been changed, value name should
   be passed as the first annotation argument.

````kotlin
@Instruction(PictureUIModel::class)
@AutoBindViewHolder(PictureViewHolder::class)
inner class PictureInstructor(
    private val viewHolder: PictureViewHolder,
    private val uiModel: PictureUIModel
) : Instructor {

    //This function will be called, when title changed.
    @BindOn("title")
    fun setTitleComplex(title: String) {
        val realDesc = "<b>$title</b> (${uiModel.tag})"
        viewHolder.setTitle(realDesc)
    }

}
````

If you completely rely on **AutoBindViewHolder**, you should create the simplest Instructor for your UI Model.

````kotlin
@Instruction(PictureUIModel::class)
@AutoBindViewHolder(PictureViewHolder::class)
inner class AutoInstructor : Instructor
````

5. Create an instance of the **Inspector** class using _Novalles.**provideInspectorFromInstructor**(instructor:
   Instructor)_ function. Better to create it outside any function, create it directly in the adapter itself.

````kotlin
private val inspector = Novalles.provideInspectorFromInstructor(PictureInstructor::class)
````

6. Invoke _Inspector.**inspectPayloads**_ with 3 arguments: your payloads (be sure you extract them properly),
   instructor and your viewHolder. This function calls corresponding functions in your viewHolder and instructor based
   on gathered payload.

### AutoBindViewHolder

Class annotated with it is considered to be the instruction how to handle payloads for UI Model. It should also
implement **Instructor** interface. Your functions should be names as _set{PropertyName}_.

````kotlin
@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    //...
    val desc: String,
    //...
) : BaseUiModel {

//...


    inner class PictureViewHolder(private val binding: ItemPictureBinding) : ViewHolder(binding.root) {

        //...

        //This function will be called, if desc changes.
        fun setDesc(desc: String) {
            binding.desc.text = desc
        }

        //...
    }
}
````

### Decompose annotations

Value, annotated with **Decompose** will be decomposed with its own values. For example, if your field have 2
properties, they will be used in any Novalles' actions separately:
Novalles will generate 2 different payloads objects in _UIModelHelper.**changePayloads**_, compare them in _
UIModelHelper.**
areContentsTheSame**_ separately.

Also, if you use **AutoBindViewHolder**, you should use **_set${FieldName}In${DecomposedFieldName}()_** functions in
your viewHolder for each field of your decomposed value.

````kotlin
data class ColorPair(
    val left: Int,
    val right: Int
)

@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    //...
    @Decompose val line: ColorPair,
    //...
) : BaseUiModel {

//...

    inner class PictureViewHolder(private val binding: ItemPictureBinding) : ViewHolder(binding.root) {

        //...

        //This function will be called, if left in line value changes.
        fun setLeftInLine(color: Int) {
            binding.colour.animateColors(color)
        }

        //This function will be called, if right in line value changes.
        fun setRightInLine(color: Int) {
            binding.colourSecond.animateColors(color)
        }

        //...
    }
}
````

### Conclusions and usage

* To maximize benefit of the Novalles, you can animate your views inside your set functions in a viewHolder.

````kotlin
fun setImage(image: Int) {
    binding.image.animateColors(image)
}
````

* Do not create any Novalles instances in functions, better to create an instance of UiModelHelper in your DiffUtil and
  an instance of Inspector in your adapter.
* Novalles does not use android components directly, so it does not have any SDK restrictions. This can be changed in
  the future, because Novalles uses _Any_ instead of _ViewHolder_ in its interfaces. This may lead to misunderstandings
  of functions usage.
* Novalles does not require any proguard rules and should work normally in release builds if your Ui Models and adapters
  work normally.
* Decompose annotation should be used careful, because it is very lightly tested.
* Please, report any issues you've encountered. **Novalles is still in development**, so your feedback is very helpful.

## How to integrate in your project

1. Add KSP dependencies in your top level gradle file.

````groovy
buildscript {
    dependencies {
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.6.21-1.0.5")
    }
}
````

2. Add KSP plugin in your app level gradle file.

````groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    //...
    id 'com.google.devtools.ksp'
}
````

3. Import the Novalles library as dependency.

````groovy
allprojects {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }
}
//...

dependencies {
    //...
    implementation 'com.github.flexeiprata:novalles:0.3.0'
    ksp 'com.github.flexeiprata:novalles:0.3.0'
    //...
}

````

## License

```
Copyright (C) 2022 FlexeiPrata.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```