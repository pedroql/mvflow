# To do ✔️

In no particular order

## General

* Ensure documenting public methods is required by CI

* Confirm release automation is working

* Write docs in a github.io page

* Add ability to set listeners of what is happening in the MVI loop

* Think about how to do analytics (probably related with listeners)... Although maybe it should use 
some sort of channels

* Think about creating a DSL for this

* Write an example of reusing views. Example I thought is a recipe list where the header is also at
the top of the detail view.
  
   * Include example of some button where the action is different from the list to the
  detail screen.
  
* Write an example of using this with recycler view (Android)

* Setup a tool to monitor API changes

* Create/publish artifacts to make it easy for people to try this out

* Setup CI to automatically run unit tests

## Core

* Decide between View implementations 1 or 2 (or 3)

```kotlin
interface MviView1<State, Action> {

    fun render(state: State)

    fun actions(): Flow<Action>
   
    // Probably useful
    fun getCoroutineScope(): CoroutineScope
}

interface MviView2<State, Action> {
    // the following method could have a better name
    fun receive(stateFlow: Flow<State>)

    fun actions(): Flow<Action>
}
```

`View1` is probably more natural to think about when you are implementing a view. On the other hand
 `View2` has the advantage automatically unsubscribing from state updates when the view is gone 
 (through coroutine cancellation mechanism). So `View2` is a bit more correct from a Coroutine
  standpoint, and is less likely to allow errors made by consumers of the library.   
  
On the other hand, we can try to implement a default method in the second interface to get the best
 of both worlds. Something like:
 
```kotlin
interface MviView3<State, Action> {
    fun render(state: State)

    fun actions(): Flow<Action>

    fun getCoroutineScope(): CoroutineScope

    // the following method could have a better name
    fun receive(stateFlow: Flow<State>) {
        getCoroutineScope().launch(Dispatchers.Main) {
            stateFlow.collect {state ->
                render(state)
            }
        }
    }
}
``` 

But this last example also raises the question: do we really need to have `receive` part of the API
 or can we just write that implementation in the library as an implementation detail?

Having it in the API allows the view to customize how it receives updates. For example, it can put a
 debounce call and can choose the dispatcher. On the other hand it's a bit of a complication that 
 most people should not need.

* Maybe `MviView` should be defined inside `MVFlow` class and renamed to simply `View`

* Think about a way to allow a `MviView<A,B>.` to be converted to a `MviView<C, D>`
 
This can greatly help reusing views. Each view could declare it's own generics and in each place you
 want to use them you map your current state into the state of this view and also map the view 
 actions into the actions in your particular loop. 
  
* Maybe MVFlow should use a hypervisor scheduler. If one child fails, do we want the others to be
 cancelled? (Maybe this could be a parameter) 
 
* Probably should call `buffer` in the actions so that slow handlers don't block whatever is 
emitting actions

* Consider changing from creating a class (MVFLow) to calling a factory or builder

* Consider if it is correct to allow to observe actions, mutations, and states
   * Maybe it would be a better alternative to allow the handler to emit new actions at any point 
 
* Tests to write:
   * If the view takes a long time to render a state and since then 2+ states come through, the view
    does not render intermediary states
   * When the MVFlow object scope is destroyed, everything stops
   * When the View scope is destroyed, the view actions and view updates stop
   * Ensure that (unlike what happened while writing tests) between setting up the view actions and
    subscribing the state to the view, there can't be missed events
   * Test what happens when actions, handler, and reducer throw exceptions

## Android

* Ability to suspend state updates when the app is not resumed (or started)

* Integration with `LiveData`
