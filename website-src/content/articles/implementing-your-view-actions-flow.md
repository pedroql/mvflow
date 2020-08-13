---
title: "Implementing Your View Actions Flow"
date: 2020-08-12T10:52:15+01:00
draft: false
---
# Implementing your view actions flow

MVFlow defines the view interface as follows:

```kotlin
interface View<State, Action> {

    fun render(state: State)

    fun actions(): Flow<Action>
}
```

Most developers should have a reasonable idea of how to implement `render(state: State)`, but implementing 
`actions(): Flow<Action>` might not seem so obvious if this is the first time you are implementing your own 
[Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html). 

We can suggest three different ways to implement this, each with different pros and cons.

## Using callback flow

`kotlinx.coroutines.core` defines a flow builder very useful for our scenario: `callbackFlow`.

Reading from the docs, this allows elements to be produced by code 
that is running in a different context or concurrently. Additionally, This builder ensures thread-safety and context 
preservation.

All of that might sound a bit vague, here's a concrete example of how you use it:

```kotlin
// in a class that implements View<State, Action>
override fun actions(): Flow<Action> = callbackFlow {
    findViewById(R.id.someButton).setOnClickListener {
        // the next line makes the flow emit a "Button pressed" value
        offer(Action.ButtonPressed)
    }
    
    // makes the flow alive until the context of the collector is cancelled 
    // (in our case this is the view context) 
    awaitClose() 
}
```

{{< custom_div class="callout" >}}

**Note**:

You can pass a lambda to `awaitClose()`  which is invoked when the context is about to be closed, 

For example, if you are using [ButterKnife](https://jakewharton.github.io/butterknife/) in a Fragment, you should call 
the `unbinder` when the view is destroyed.
{{< / custom_div >}}

You can read more about `callbackFlow` in the
[official documentation](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html)

Pros:

* Simple

* Full control of emissions

* Easy to reason

* Coroutine scope handled automatically

Cons:

* Might not be so easy (or clean) to apply this pattern when you need to emit values from items inside a recycler view.

## Using a channel

The main problem in the previous approach is that in some scenarios you might want to give another class the 
ability to send values into the flow and that's not easy to achieve.

For example, if you have a non-trivial recycler view, you might want the adapter to be able to send values such as
"tapped on the second button of the third row".

When your view is created, you probably would like to create the recycler view adapter and have something it could use 
to send values, but at this time the flow hasn't been created yet so you don't have a good way to do it.

One solution is to have a mutable nullable property (or a lateinit property) in the adapter, and once you have a flow you
 can set it. Although this approach is valid, there is one alternative which we explain now. Each of these options have
different tradeoffs and it is your choice which one to use.

If you want to solve this problem using a 
[channel](https://kotlinlang.org/docs/reference/coroutines/channels.html), you can follow this approach:

```kotlin
// in a class that implements View<State, Action>
// (usually your activity or fragment)
private val actionChannel = Channel<Action>()
private val adapter = MyAdapter(actionChannel)

// call this when the view is inflated
fun setupUi() {
    with(recyclerView) {        // ...
        adapter = this@MyActivity.adapter
    }
    swipeRefreshLayout.setOnRefreshListener {
        actionChannel.offer(Action.LoadUsers)
    }
}

override fun render(state: State) // ...

override fun actions() = actionChannel.consumeAsFlow()

override fun onDestroy() {
    super.onDestroy()
    actionChannel.close()
}
```

And your adapter looks like this:

```kotlin
class UserRecyclerViewAdapter(
    private val sendChannel: SendChannel<MainView.Action>
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // inflate row
        return ViewHolder().apply {
            row.findViewById(R.id.button2).setOnClickListener {
                val user = users[adapterPosition]
                sendChannel.offer(Action.Button2(user))
            }    
        }
    }
}
```

Pros:

* Easier to allow different classes emit values into the flow

* No need for mutable, nullable, or late init properties

Cons:

* Need to take care of closing the channel

## Using a third party library

You can also use another library to create a flow from the user actions. 
[FlowBinding](https://github.com/ReactiveCircus/FlowBinding) is one example and you might find others.

```kotlin
// in a class that implements View<State, Action>
val flow1: Flow<Actions> = findViewById<Button>(R.id.button)
    .clicks()
    .map { Actions.Proceed}

val flow2: Flow<Actions> = findViewById<Button>(R.id.checkbox)
    .checkedChanges()
    .map { checked -> Actions.DoMore(checked) }

override fun actions() = merge(flow1,flow2)
``` 

Pros:

* Library provides useful bindings

* You might be used to this pattern if you used RxBindings

Cons:

* You might still need some custom handling for something that doesn't fit any binding provided by the library
