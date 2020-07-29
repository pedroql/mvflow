# MVFlow
**Simple Android* MVI architecture using kotlin flows**

 _\* The architecture is platform-neutral but was designed with Android in mind_

## What are we trying to achieve?

There are plenty of architectural libraries out there. Plenty of combinations for all tastes, but we
 couldn't find one with everything we were looking for.

We set off with the objective of creating a minimalistic library, simple yet with all the 
capabilities you need. Plus, easy to get started. All these points are discussed in further detail 
below.

We have built a library that lets you easily manage the state of your screen using a MVI approach.
It uses coroutines and flows to achieve most of the work while keeping the library easy and simple.
Here are some things we really like in this approach:

* Coroutines make asynchronous calls very simple to write and easy to reason about;

* Flows are a great abstraction to represent user events (clicks) and updates from ongoing work;

* Coroutine scopes make handling the lifecycle of requests very simple

  * For example: if you have pending work for a particular screen, and the user presses back, by 
  cancelling that screen's coroutine, all related jobs and listeners are cancelled too. 
  Read more about 
  [structured concurrency](https://kotlinlang.org/docs/reference/coroutines/basics.html#structured-concurrency); 


## Objectives

### Minimalistic yet complete

We believe that our tool choices are so powerful that our library can do a lot with very little 
code. Additionally, we don't want to force you to use a bigger library than what you need.
However, you can also see that these objectives might be conflicting at times. For example, it's hard
 to be feature complete yet have a very small library.

In its initial version of the library, `mvflow-core` contains the whole logic in one single file. 
That's all you need to read to completely understand how to work with this library! 

Upcoming work will add more features but because they are not part of the core functionality, they
can be provided in different modules that you can choose to use.

We will aim at keeping the core logic of the library in one file so that it's easy to copy paste this
into your project should you decide to tailor it exactly to your project needs. One file. 100% yours.

### Simple

MVFlow introduces few - if any - new concepts specific to this library. 

That is not to say that you don't need to know anything to use it. But you may have been exposed
to these concepts before and you will be able to apply that knowledge immediately.

On the other hand, the things that you learn by using this library are not specific to this library
so you will be able to transfer this acquired knowledge to other parts of your regular work.

Here's what you need to know to master this library:

* MVI architecture

* Kotlin coroutines and flows

We won't focus too much in teaching these foundations here because there are already many great 
resources available online. 

We suggest [Hannes Dorfmann](http://hannesdorfmann.com/android/mosby3-mvi-1) intro to MVI. Mainly 
parts [2](http://hannesdorfmann.com/android/mosby3-mvi-2) and 
[3](http://hannesdorfmann.com/android/mosby3-mvi-3). And you have the 
[kotlin official guide](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html) for 
coroutines and flows. 


### Easy to get started

Because the library is very focused, it has a small API surface. On top of that, because it doesn't 
 introduce many new concepts, we believe this combination makes it easy for someone to get started.

We believe that if we show you some code, even without knowing the inner workings of the library, 
you will understand what is happening. 

## How to MVFlow

### Introduction

_Note: these examples hide optional methods and parameters_

In `MVFlow` there is a `MviView`. 

The main things you need to worry about when implementing this are providing a flow of user `Action`s 
and rendering the `State` whenever it is updated. You also need to return the Coroutine scope of the
 view.
 
 ```kotlin
interface MviView<State, Action> {
    fun render(state: State)

    fun actions(): Flow<Action>

    val coroutineScope: CoroutineScope
} 
```

When you want to construct a `MVFlow` object to manage a screen, you need to pass the initial state,
a `Handler`, and a `Reducer`.

The `Handler` is a lambda that receives the current state and an `Action` that just happened. The
 handler is not allowed to modify anything directly. Instead, it returns a flow where it emits any 
 `Mutation`s which the `Reducer` will receive and actually modify the state.
 

```kotlin
typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>
typealias Reducer<State, Mutation> = (State, Mutation) -> State

class MVFlow<State, Action, Mutation>(
    initialState: State,
    private val handler: Handler<State, Action, Mutation>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope
) 
```

You can still use your favourite way to render the UI, make network requests, handle navigation, 
etc. 

### Example

Let's think of a generic screen with a list of users:

The actions here are very simple: Load data (which can be triggered by doing a pull to refresh 
gesture) and tapping a row (to see the details of this user).

The state is also simple: It contains a list of users, whether we are loading data, and a error 
message.

Let's start with this:

```kotlin
data class User( 
     // not in scope 
)

data class State (
    val users: List<User>?,
    val isLoading: Boolean,
    val errorMessage: String?
)

sealed class Action {
    object LoadUsers : Action()
    data class UserSelection(val user: User) : Action()
}
```
 
 Next step: what changed of data might happen? We don't need to think about this in advance. As we 
 implement the handler we will see what kind of mutations we need. But we can do that in this 
 simple scenario:
 
 ```kotlin
sealed class Mutation {
    object StartedLoading : Mutation()
    data class ErrorLoading(val message: String) : Mutation()
    data class UsersReceived(val users: List<User>) : Mutation()
}
```

Now, onward to the `Reducer`:

```kotlin
typealias Reducer<State, Mutation> = (State, Mutation) -> State

val reducer: Reducer<State, Mutation> = { state, mutation ->
    when (mutation) {
        StartedLoading -> state.copy(isLoading = true)
        is ErrorLoading -> state.copy(
            errorMessage = mutation.message,
            isLoading = false
        )
        is UsersReceived -> state.copy(
            hasError = null,
            isLoading = false,
            users = mutation.users
        )
    }
} 
```

The reducer is very simple. The mutation object should contain all the information the reducer needs
to compute the new state.

Now it's time to see the `Handler`:

```kotlin
typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>

val handler: Handler<State, Action, Mutation> = { state, action ->
      when (action) {
          Action.LoadUsers -> {
              flow {
                  emit(Mutation.StartedLoading)
                  val users = userRepository.getUsers()
                  emit(Mutation.UsersReceived(users))
              }.catch { throwable ->
                  // network problems (and anything else) will come here
                  val messageToDisplay = throwableToMessage(throwable)
                  emit(Mutation.ErrorLoading(messageToDisplay))
              }
          }
          is UserSelection -> {
              openDetailScreen(action.user)
              emptyFlow()
          }
      }
}

fun throwableToMessage(t: Throwable) = "Something went wrong"
```

The last piece of the puzzle is the `MviView`. This is just an interface, so you can use whatever 
you like. You can implement it as part of your Activity, Fragment, or your own class that receives a 
`View` (the root) in its constructor.

```kotlin

class MyActivity : AppCompatActivity(), MviView<State> {
    // could also be a Fragment, or a plain class you created.
    // note: not all code for UI functionality is included
    private val actionChannel = Channel<Action>()
    private val adapter = UserRecylerViewAdapter(actionChannel)

    fun setupUi() {
        with(recyclerView) {
            // ...
            adapter = this@MyActivity.adapter
        }
        swipeRefreshLayout.setOnRefreshListener {
            actionChannel.offer(Action.LoadUsers)
        }
    }

    override fun render(state: State) {
        errorMessage.visibility = if (state.hasError) View.VISIBLE else View.GONE
        swipeRefreshLayout.isRefreshing = state.isLoading
        adapter.users = state.users
    }
    
    override fun actions() = actionChannel.consumeAsFlow()
    
    override fun coroutineScope() = lifecycleScope
}
```

In this example you see one way to create a `Flow` for the user actions, but there are alternatives.
We suggest having a look at `FlowBinding` [library](https://github.com/ReactiveCircus/FlowBinding) 
which provides bindings for many UI events and enables you to write this code:

```kotlin
sealed class Actions {
    object Proceed: Actions()
    data class ExtraJuice(val extra: Boolean): Actions()
}

val flow1: Flow<Actions> = findViewById<Button>(R.id.button)
    .clicks()
    .map { Actions.Proceed}

val flow2: Flow<Actions> = findViewById<Button>(R.id.checkbox)
    .checkedChanges()
    .map { checked -> Actions.ExtraJuice(checked) }

override fun actions() = merge(flow1,flow2)
```   

Putting it all together:

The final step is instantiating the `MVFlow` object (we've seen each part individually so far) and 
then we can plug our view to it:

```kotlin
// Call this constructor
class MVFlow<State, Action, Mutation>(
    initialState: State,
    private val handler: Handler<State, Action, Mutation>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope
) 

val flow = MVFlow( 
    // ...
)

// we've also seen an activity implementing the view interface
val myView: MviView = // ...

flow.takeView(myView)
```

At the end of that code snippet, the view will render the initial state. Additionally, any actions
the user makes will be sent to the handler which in turn can send mutations that are reduced to a 
new state by our reducer, hence closing the loop.

If you were paying attention, you will see that `MVFlow` takes a coroutine scope. This is useful for
scenarios where this object should outlive the view scope. For example, in Android you might choose 
to retain the flow object but the view is killed by the system. When a new view is created, you can
use the old flow object and give it a new view to continue the loop.

In Android, if you want the flow object to survive orientation changes (hence outliving the view), 
can should pass the `ViewModel`'s coroutine scope `Activity::viewModel.viewModelScope`. This means that 
everything will still be destroyed when the user navigates away from your screen.

In other cases where you just want the flow object to live as long as your view, you can pass the
activity or fragment's scope (`Activity::lifecycleScope`).

### Gotchas

There are a few things to keep in mind:

* At any point there might be several actions being processed by the `Handler`. For example, one 
slow request might be taking place while a second quicker action takes place. If the second action
modifies the state, that means the slow request no longer has visibility into the most up to date 
state.

  * This should not be a problem in most cases. The handler might need the state as of when the
   action started to be processed (for example, if the user presses submit in a form, the state
  would contain the values in that form). If the handler wants to make update the state, the 
  mutation it emits should contain *how to modify* the state, but not the actual value(s). A counter
  should emit `Mutation.IncrementBy(5)` instead of ~~`Mutation.SetValue(state.counter+5)`~~ 

* In order to keep a consistent state we can only process mutations one at a time. So make sure your
`Reducer` implementation is quick. Most of the logic should be done by the `Handler`.

* State updates are [conflated](https://kotlinlang.org/docs/reference/coroutines/flow.html#conflation). 
If your view is still executing the previous `render` call and 
there are several updates to the state, the view is only called to render the latest state 
(intermediary states are dropped). If you follow the principles of MVI this is not a problem.

## Inspiration

Unsurprisingly this library got a lot of inspiration from other libraries. We would like to thank:

* [Orbit MVI](https://github.com/babylonhealth/orbit-mvi/)
* [MVICore](https://badoo.github.io/MVICore/)
* [Knot](https://github.com/beworker/knot)

And everyone who contributed towards those libraries (and their respective inspirations).

## Current state, feedback, and contributions

```
Version 0.0.3
```

We believe the current API is getting more stable and closer to what 1.0 will look like. 
You can get it from maven central:

```
dependencies {
    implementation 'net.pedroloureiro.mvflow:mvflow-core:0.0.3'
}
```

**Please let us know what you think.** 

If you would like to contribute back, just create a PR, new issue, or comment on open PRs.

If you create a PR, please make sure you unit test and document the code you write.

If you are looking for ideas, this is a very rough list of what we are planning [to do](todo.md).
