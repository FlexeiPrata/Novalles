# Novalles 

*A library to simplify and speed up building RecyclerView adapters that work with payloads.*

[![License](https://img.shields.io/badge/License%20-Apache%202-337ab7.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![](https://jitpack.io/v/FlexeiPrata/Novalles.svg)](https://jitpack.io/#FlexeiPrata/Novalles)

****

## How to use

1. Annotate your UI model with the **UIModel** annotation.

````kotlin
@UIModel
data class PictureUIModel(
    @PrimaryTag val tag: String,
    val image: Int,
    @NonUIProperty val imageCode: String,
    val title: String,
    val desc: String,
    val likes: Int
) : BaseUiModel
````

* Use the **PrimaryTag** annotation to mark the property used in the `areItemsTheSame` comparison.
* Use the **NonUIProperty** annotation to mark a property that should be left out of every comparison.

2. Create a **_UIModelHelper_** for your DiffUtil with **provideUiInterfaceForAsFromCatalogue**.

````kotlin
private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAsFromCatalogue(PictureUIModel::class)
````

**Note**: To enable the catalogue feature, create a single main catalogue class or object annotated with **NovallesCatalogue**. For large projects, you can split your models across separate **NovallesPage** classes and reference them from the main catalogue.
The catalogue is faster because it avoids reflection and calls the generated constructors directly, and it is the recommended way to provide Novalles instances.
If a model isn't in the catalogue, Novalles falls back to reflection and prints a warning, unless you turn on strict catalogue mode (see below). The older **provideUiInterfaceFor** and **provideUiInterfaceForAs** always use reflection and are deprecated, so calls to them no longer compile.

````kotlin
//Simple catalogue - all models in one place
@NovallesCatalogue(pages = [MainPage::class])
object MainCatalogue

@NovallesPage
object MainPage

//Or organize large projects into pages
@NovallesCatalogue(pages = [PicturesPage::class, UsersPage::class])
object MainCatalogue

//Pictures module
@NovallesPage
object PicturesPage

//Users module
@NovallesPage
object UsersPage
````

3. Call the matching _UIModelHelper_ functions in your DiffUtil. This example uses a DiffUtil built on a common interface.

````kotlin
class DefaultDiffUtil<T : BaseUiModel>(uiModel: KClass<T>) : DiffUtil.ItemCallback<BaseUiModel>() {

    private val uiModelHelper: UIModelHelper<BaseUiModel> = Novalles.provideUiInterfaceForAsFromCatalogue(uiModel)

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

4. Create a class that implements the **Instructor** interface. Annotate it with **Instruction** and pass your _UI
   Model_ class as the annotation argument. You can also annotate it with **BindViewHolder** _(see the corresponding
   section for details)_. Inside this class, create the functions that should run when a value changes.
Use the **BindOn** annotation to tell Novalles which function to call when a given value changes — pass the value name
   as the first annotation argument. By default, a bound function is called in both the _bind_ and _payloads_ flows.
If you want to tell them apart, add a **boolean argument** to the function: **true** means it was called from bind, **false** from payloads.
If the name doesn't match a property of your UI Model, you'll get a warning during the build. **PrimaryTag** and **NonUIProperty** properties don't count, since they never produce payloads.

````kotlin
@Instruction(PictureUIModel::class)
@BindViewHolder(PictureViewHolder::class)
inner class PictureInstructor(
    private val viewHolder: PictureViewHolder,
    private val uiModel: PictureUIModel
) : Instructor {

    //This function is called when the title changes.
    @BindOn("title")
    fun setTitleComplex() {
        val realDesc = "<b>${uiModel.title}</b> (${uiModel.tag})"
        viewHolder.setTitle(realDesc)
    }

    //This function is called when the image changes. It receives "true" when called from bind,
    //and "false" when called from payloads.
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

//This function is called when the title or the count changes.
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

5. Create an **Inspector** from your UI Model or from your Instructor.

````kotlin
private val inspector = Novalles.provideInspectorFromModelCatalogue(PictureUIModel::class)
//or
private val inspector = Novalles.provideInspectorFromInstructorCatalogue(PictureInstructor::class)
````

The reflection-based **provideInspectorFromUiModel**, **provideInspectorFromUiModelRaw** and **provideInspectorFromInstructor** are deprecated, so calls to them no longer compile.

6. Invoke _Inspector.**inspectPayloads**_ with four arguments: your payload,
   the instructor, your viewHolder, and a lambda that runs when the payload is empty. Based on the collected payload, it calls
   the matching functions in your viewHolder and instructor.
7. You can call the inspector's **bind** function to run every function bound to a field in your viewHolder or instructor.

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

A class annotated with it is treated as the instruction for how to handle a UI Model's payloads. It should also
implement the **Instructor** interface. Name your functions as _{prefix}{PropertyName}_.
The default prefix is **set**, and the default bindPrefix is **bind**. By default, a bound function is called in both
the _bind_ and _payloads_ flows.
You'll get warnings during KSP generation if the matching functions are missing from the Instructor and your viewHolder.
To tell the two flows apart, use the prefixes: **bind** for the bind call, and **set** _(or whatever you configured)_ for the payloads call.

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

        //This function is called when desc changes.
        fun setDesc(desc: String) {
            binding.desc.text = desc
        }

        //This function is called on bind.
        fun bindImage(image: String) {
            binding.image.load("image")
        }

        //This function is called when image changes.
        fun setImage(image: String) {
            binding.image.loadAnimated("image")
        }

        //...
    }
}
````

### Manual notifying
You can notify your adapter manually using **BindOnTag**.
1. Create a tag: a sealed class, or any object that has its own type.
````kotlin
sealed interface Tags {
    object SetNullLikes
}
````
2. Register this tag in your **Instructor** with **BindOnTag**.
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
3. Call the relevant notify function and pass this tag as the payload.
````kotlin
novallesAdapter.notifyItemRangeChanged(0, novallesAdapter.itemCount, Tags.SetNullLikes)
````
### Tips and notes

* To get the most out of Novalles, animate your views inside the set functions in your viewHolder.

````kotlin
fun setImage(image: Int) {
    binding.image.animateColors(image)
}
````

* Don't create Novalles instances inside functions. Instead, create the UIModelHelper in your DiffUtil and
  the Inspector in your adapter.
* Novalles doesn't use Android components directly, so it has no SDK restrictions. That may change in the future;
  for now its interfaces use _Any_ instead of _ViewHolder_, which can make their usage a little less obvious.
* With the catalogue, Novalles doesn't require any ProGuard rules and should work fine in release builds, as long as your UI Models and
  adapters do. If a model is missing from the catalogue, the reflection fallback looks classes up by name, which R8 can break.
* Please report any issues you run into. Your feedback really helps.

## How to integrate into your project

Novalles is built with Kotlin 2.2.20 and KSP 2.2.20-2.0.3. Your project needs Kotlin 2.1 or newer, and Gradle has to run on JDK 17 or newer.

1. Add the KSP dependency to your top-level Gradle file.

````groovy
buildscript {
    dependencies {
        //You can use a different Kotlin version.
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${version}")
    }
}
````

2. Add the KSP plugin to your app-level Gradle file.

````groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    //...
    id 'com.google.devtools.ksp'
}
````

3. Add the Novalles library as a dependency.

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

### Updating from 0.13

* Novalles no longer adds **kotlin-reflect** to your app. If your own code uses it, add the dependency yourself.
* Calls to the reflection-based provide functions, **provideCachedUiInterfaceForAs** and **clearCache** no longer compile.
  Your IDE can replace each provide function with its catalogue version.
* Novalles checks more at build time. A few mistakes that used to compile, like an **Instruction** pointing to a class
  without **UIModel**, now fail with a message that points to your code.

### Strict catalogue mode

If you want to be sure Novalles never falls back to reflection, turn on strict catalogue mode in every module
that uses Novalles. It is off by default.

````groovy
ksp {
    arg("novalles.strictCatalogue", "true")
}
````

With it, the build fails if a module has UI models but no **NovallesPage**, or if the module's page is missing from
your **NovallesCatalogue**. What can't be checked during the build fails at runtime instead: the catalogue throws an
exception for a class it doesn't know, and so does any attempt to create a generated class with reflection.

### Troubleshooting: incremental builds

Novalles declares precise KSP dependencies for each generated file, so an edit only
regenerates what it actually affects. If you ever suspect a generated file is stale or
incomplete, you can turn that off:

````groovy
ksp {
    arg("novalles.safeDependencies", "true")
}
````

This associates every generated file with every source file, which **disables incremental
processing** and makes each build reprocess everything. It is a diagnostic switch, not a
setting to leave on — and please report the issue if you need it.

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