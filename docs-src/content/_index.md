---
title: "MVFLow: a simple MVI library using Kotlin coroutines and flows"
date: 2020-08-04T13:27:21+01:00
draft: true
---

# MVFlow
**Simple Android\* MVI architecture using kotlin flows**

 _\* The architecture is platform-neutral but was designed with Android in mind_

```groovy
dependencies {
    implementation 'net.pedroloureiro.mvflow:mvflow-core:<latest version>'
}
```

Check our [releases on github](https://github.com/pedroql/mvflow/releases) to find the latest one.

## Objectives

We set off with the objective of creating a minimalistic library, simple yet with all the 
capabilities you need.


### Minimalistic yet complete

MVFlow has a very small API surface. It strives to be the smallest conceivable MVI library that could exist. 
In just a few minutes you will know everything there is to know about the API! 

### Simple

The library introduces few - if any - new concepts outside MVI, 
[Kotlin coroutines](https://kotlinlang.org/docs/reference/coroutines/basics.html), and 
[Kotlin flows](https://kotlinlang.org/docs/reference/coroutines/flow.html). 

If you familiar with those concepts, you can start using the library without a problem; if you are new to some of these
concepts, you will be able to apply those concepts outside this library too.

### Coroutines and flows are powerful abstractions


Any MVI library needs to accomplish the following tasks:

* Manage the current state (Model in MVI)

* Call a render function when the state changes (View in MVI)

* Detect user interactions (Intent in MVI)

 * Process user intents, potentially mutating the current state  
 
&nbsp;

We believe that coroutines and flows are extremely powerful concepts that can be applied to this problem. 
This gives a very powerful API with a small and simple surface.

Here are some advantages that they bring:

* Coroutines make asynchronous calls very simple to write and easy to reason about;

* Flows are a great abstraction to represent user events (clicks) and updates from background work;

* Coroutine scopes make handling the lifecycle of requests very simple

  * For example: if you have pending work for a particular screen, and the user presses back, by 
  cancelling that screen's coroutine, all related jobs and listeners are cancelled too. 
  Read more about 
  [structured concurrency](https://kotlinlang.org/docs/reference/coroutines/basics.html#structured-concurrency); 

### With MVFlow you are in control

The library only gives you an implementation of MVI, but doesn't force you into specific patterns other than using MVI
and coroutines.

You are free to use dependency injection your way; you are free to do navigation your way; plug your 
logging framework (or not). You can use data stores, use cases, interactors, or any other abstraction to get and 
manipulate your data. 

MVFlow plays well with Android's ViewModel, but you don't have to use it.  

&nbsp;

&nbsp;

## What if I am new to MVI, coroutines, or flows?

TODO - find links 

* Coroutines official guide
* [Flow official guide](https://kotlinlang.org/docs/reference/coroutines/flow.html)

&nbsp;

&nbsp;

## API

### View

The library has two main interfaces: `MVFlow` and `MVFlow.View`. Let's take a look at the view first:

```kotlin
interface View<State, Action> {

    fun render(state: State)

    fun actions(): Flow<Action>
}
```

As you can see, this is a very simple interface: any view needs to know how to render a given state (which is a generic 
class defined by you) and creates a flow of actions which 
report the user interactions with the UI.

TODO - More details on how you can create such flow.

Let's implement a simple screen with a list of users. The current state indicates whether it is loading information, the
users that were loaded (if any) and a error message (if any):

```kotlin
data class User( 
     // ... 
)

data class State (
    val users: List<User>?,
    val isLoading: Boolean,
    val errorMessage: String?
)
```

The possible actions are loading users (say, by pressing a button or doing a pull-to-refresh gesture) and selecting a 
user by tapping on its row:
 
```kotlin
sealed class Action {
    object LoadUsers : Action()
    data class UserSelection(val user: User) : Action()
}
```

### Handler

So now that we have a view, we can render the UI and send actions into a flow when the user interacts with the app.

The next step is doing something with those actions. Typically, this involves doing some operation like a network or 
database call, and we might want to change the current state from said jobs.  

This is where `Handler` comes in. To keep things simple, this is just a lambda but it is type-aliased for more 
meaningful parameter names:

```kotlin
typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>
```

Reading that aloud, we see that a handler is a function that receives the current state and an action the user just 
performed. The handler performs what this action requires (e.g. making a network call) and returns a flow of mutations. 
A mutation is the information of what needs to change in the current state. 
When the handler completes the network request, it can emit a value into the mutation flow to update the state.

Continuing our example, there are three reasons to change the state: we started loading data, we received an error from
the request or we received data (new users) from the request:   

 ```kotlin
sealed class Mutation {
    object StartedLoading : Mutation()
    data class ErrorLoading(val message: String) : Mutation()
    data class UsersReceived(val users: List<User>) : Mutation()
}

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

### Reducer

Right now we have a view rendering the state and telling us of actions performed by the user. We also have a handler
that is receiving those actions and acting on them. The handler emits mutations that we already know will be used to 
update the current state. 

Now the reducer enters the stage:

A reducer is just another function that receives the current state and a mutation. It will combine both, producing
the a new state the succeeds the current one.

_Note: although the handler receives the current state when an action is performed, if the handler performs a slow 
operation, the handler will no longer know the most recent state. This is why the handler can't mutate the state 
directly._

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

### MVFlow object

At this point we have explained the main MVI components. The final step is assembling them together so that actions are
sent to the handler, mutations are sent to the reducer, and new states are sent to the view.

```kotlin
typealias Logger = (String) -> Unit

interface MVFlow<State, Action> {
  
    fun takeView(
        viewCoroutineScope: CoroutineScope,
        view: View<State, Action>,
        initialActions: List<Action> = emptyList(),
        logger: Logger? = null
    )

    /**
     * This method adds an external source of actions into the MVFlow object.
     *
     * This might be useful if you need to update your state based on things
     * happening outside the [View], such as timers, external database updates,
     * push notifications, etc.
     */
    fun addExternalActions(
        actions: Flow<Action>,
        logger: Logger? = null
    )
} 
```

All you need to do is to create a `MVFlow` object and call `takeView` on it. And the loop will start!

Creating on MVFlow object is as simple as calling a factory method: 

```kotlin
fun <State, Action, Mutation> MVFlow(
    initialState: State,
    handler: Handler<State, Action, Mutation>,
    reducer: Reducer<State, Mutation>,
    // the scope of this object can outlive the scope of the view
    mvflowCoroutineScope: CoroutineScope,
    defaultLogger: Logger = {}
): MVFlow<State, Action>
```

And then call `takeView`:

```kotlin
// in your activity
lifecycleScope.launchWhenStarted { // this: CoroutineScope
    mvFlow.takeView(this, view)
}
```

If you are wondering what `Logger` is, that is just a method that receives one `String`. The library will call this
with logging information and you can decide to print this to the logs or not.

### Special effects

Effects are an optional feature that can be very useful for UI-related operations like showing 
Toasts and navigating to new screens.

Because the handler may have a different lifecycle from the view, it should not perform these operations directly (it 
would also break the single responsibility principle).

To make this common scenario easier to address, there is a new set of interfaces that extend the interfaces we have seen
until here. These interfaces have the suffix `WithEffects` and bring additional functionality to make external effects 
possible.

The changes are very simple:

The `Handler` instead of just receiving the current state and one action, receives one effect sender that can be used to 
send effects to be handled externally. `EffectSender` just exposes the two methods you can use to send values to a 
[SendChannel](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-send-channel/index.html) 

```kotlin

interface EffectSender<T> {

    // send might suspend if the channel is full
    suspend fun send(effect: T)

    // offer never suspends nor blocks;
    // returns true if the effect was successfully submitted to the channel 
    fun offer(effect: T): Boolean
}

typealias HandlerWithEffects<State, Action, Mutation, Effect> =
    (State, Action, EffectSender<Effect>) -> Flow<Mutation>
```

Now that you have a `HandlerWithEffects`, you can call another factory method which has the same name as before. It has 
only one difference in the signature:
 
{{< highlight kotlin "hl_lines=4" >}}
// this is a different factory method
fun <State, Action, Mutation, Effect> MVFlow(
    initialState: State,
    handler: HandlerWithEffects<State, Action, Mutation, Effect>,
    reducer: Reducer<State, Mutation>,
    mvflowCoroutineScope: CoroutineScope,
    defaultLogger: Logger = {}
): MVFlowWithEffects<State, Action, Effect>
{{< / highlight >}}

Now that you have got a `MVFlowWithEffects`, you have one more method that you can use:
```kotlin
interface MVFlowWithEffects<State, Action, Effect> : MVFlow<State, Action> {

    fun observeEffects(): Flow<Effect>
}

// in your activity:
lifecycleScope.launchWhenResumed { // this: CoroutineScope
    mvFlow.observeEffects().collect{ effect -> 
        // do something
    }
}
 ``` 

As a bonus, this is a way to ensure you only do navigation transations only during the resumed state, saving you from the dreaded

```
Exception java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
```

&nbsp;

&nbsp;

## Additional Reading

There are some additional topics you might want to read about:

* Read the full KDocs

* Browse the source code of the sample android app

* Implementing the view flow

TODO: write posts and add links here (or other sections of the website)

&nbsp;

&nbsp;

## Inspiration

This library got a lot of inspiration from other libraries. We would like to thank:

* [Orbit MVI](https://github.com/babylonhealth/orbit-mvi/)
* [MVICore](https://badoo.github.io/MVICore/)
* [Knot](https://github.com/beworker/knot)

And everyone who contributed towards those libraries (and their respective inspirations).
