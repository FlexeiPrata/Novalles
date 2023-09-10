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
private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAs(PictureUIModel::class)
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
   Model_ class as the annotation argument. You can also annotate it with **BindViewHolder** _(See corresponding
   section for more details)_. In this class you should create functions, that will be called on a value change. 
Use **BindOn** annotation to tell Novalles which function should be called when a value has been changed, value name should
   be passed as the first annotation argument. By default, bound function will be called both in _bind_ and _payloads_ flows.
If you want to differ them, you can add a **boolean argument** in this function. **True** stands for bind call, **False** for payloads call.

````kotlin
@Instruction(PictureUIModel::class)
@BindViewHolder(PictureViewHolder::class)
inner class PictureInstructor(
    private val viewHolder: PictureViewHolder,
    private val uiModel: PictureUIModel
) : Instructor {

    //This function will be called, when title changes.
    @BindOn("title")
    fun setTitleComplex() {
        val realDesc = "<b>${uiModel.title}</b> (${uiModel.tag})"
        viewHolder.setTitle(realDesc)
    }

    //This function will be called when image changes. It will be called with "true" if it is called from bind,
    //And with "false" if called from payloads.
    @BindOn("image")
    fun bindTestImage(isFromBind: Boolean) {
        if (isFromBind) {
            viewHolder.setImage(uiModel.image)
        } else {
            viewHolder.setImageAnimated(uiModel.image)
        }
    }
    

}
````

You can bind more than one field at once:
````kotlin

//This function will be called, when title or count changed.
@BindOnFields(["title", "count"])
fun setTitleAndCount() {
    val realDesc = "<b>${uiModel.title}</b> (${uiModel.count})"
    viewHolder.setTitle(realDesc)
}
    
````

If you completely rely on **BindViewHolder**, you can create the simplest Instructor for your UI Model.

````kotlin
@Instruction(PictureUIModel::class)
@BindViewHolder(PictureViewHolder::class)
inner class AutoInstructor : Instructor
````

5. Create an instance of the **Inspector** class using _Novalles.**provideInspectorFromUiModel<uiModelClass>**()_ 
function. Better to create it outside any function, so create it directly in the adapter itself.

````kotlin
private val inspector = Novalles.provideInspectorFromUiModel<PictureUIModel>()
````

6. Invoke _Inspector.**inspectPayloads**_ with 4 arguments: your payload,
   instructor, your viewHolder and lambda for action when payload is empty. This function calls corresponding functions in your viewHolder and instructor based
   on gathered payload.
7. You can call **bind** function of your inspector to call all functions bound to fields in your viewHolder or instructor.

````kotlin
override fun onBindViewHolder(
    holder: PictureViewHolder,
    position: Int,
    payloads: MutableList<Any>
) {
    val model = currentList[position] as PictureUIModel
    val instructor = PictureInstructor(
        viewHolder = holder,
        model
    )
    inspector.inspectPayloads(payloads, instructor, viewHolder = holder) {
        inspector.bind(model, holder, instructor)
        holder.setOnClickActions(model, onClick)
    }
}
````

### BindViewHolder

Class annotated with it is considered to be the instruction how to handle payloads for UI Model. It should also
implement **Instructor** interface. Your functions should be names as _{prefix}{PropertyName}_.
The default prefix is **set**. By default, bound function will be called both in _bind_ and _payloads_ flows.
The default bindPrefix is **bind**.
You'll get warnings during ksp generating, if there will be no corresponding functions in [Instructor] and your [viewHolder].
If you want to differ them, you can change prefix: **bind** stands for bind call, **set _(or other configured)_** for payloads call.

````kotlin
@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    //...
    val desc: String,
    val image: String,
    //...
) : BaseUiModel {

//...


    inner class PictureViewHolder(private val binding: ItemPictureBinding) : ViewHolder(binding.root) {

        //...

        //This function will be called, if desc changes.
        fun setDesc(desc: String) {
            binding.desc.text = desc
        }

        //This function will be called on bind.
        fun bindImage(image: String) {
            binding.image.load("image")
        }

        //This function will be called, if image changes.
        fun setImage(image: String) {
            binding.image.loadAnimated("image")
        }

        //...
    }
}
````

### Decompose annotations

Value, annotated with **Decompose** will be decomposed with its own values. For example, if your field have 2
properties, they will be used in any Novalles' actions separately:
Novalles will generate 2 different payloads objects in _UIModelHelper.**changePayloads**_, compare them in _UIModelHelper.**areContentsTheSame**_ separately.

Also, if you use **BindViewHolder**, you should use _{prefix}${FieldName}In${DecomposedFieldName}()_ functions in
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

### Manual notifying
You can manually notify your adapter with **BindOnTag** usage.
1. Create some sealed class or other instance that has its own class.
````kotlin
sealed interface Tags {
    object SetNullLikes
}
````
2. Register this tag in your **Instructor** with **BindOnTag**
````kotlin
@Instruction(PictureUIModel::class)
@BindViewHolder(PictureViewHolder::class)
inner class PictureInstructor(
    private val viewHolder: PictureViewHolder,
    private val uiModel: PictureUIModel
) : Instructor {

    //...

    @BindOnTag(Tags.SetNullLikes::class)
    fun setZeroLikes() {
        viewHolder.setLikes(0)
    }

}
````
3. Call necessary function and pass this tag as a Payload.
````kotlin
novallesAdapter.notifyItemRangeChanged(0, novallesAdapter.itemCount, Tags.SetNullLikes)
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
        //You can use other Kotlin version.
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${version}")
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
    implementation "com.github.flexeiprata:novalles:${currentVersion}"
    ksp "com.github.flexeiprata:novalles:${currentVersion}"
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