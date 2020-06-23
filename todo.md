# To do ✔️
## General

* Add a micro module to enable copy paste of one single file to get the most functionality of this library

* Add ability to set listeners of what is happening in the MVI loop

* Think about how to do analytics (probably related with listeners)... Although maybe it should use some sort
of channels

* Think about creating a DSL for this

* Write an example of reusing views. Example I thought is a recipe list where the header is also at the top
 of the detail view.
  
   * Include example of some button where the action is different from the list to the
  detail screen.
  
* Write an example of using this with recycler view (Android)

* Setup a tool to monitor API changes

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

`View1` is probably more natural to think about when you are implementing a view. On the other hand `View2`
 has the advantage automatically unsubscribing from state updates when the view is gone (through coroutines
 cancellation mechanism). So `View2` is a bit more correct from a Coroutine standpoint, and is less likely
 to allow errors made by consumers of the library.   
  
On the other hand, we can try to implement a default method in the second interface to get the best of both
 worlds. Something like:
 
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

But this last example also raises the question: do we really need to have `receive` part of the API or can we
just write that implementation in the library as an implementation detail?

Having it in the API allows the view to customize how it receives updates. For example, it can put a
 debounce call and can choose the dispatcher. On the other hand it's a bit of a complication that most people
  should not need.

* Maybe `MviView` should be defined inside `MVFlow` class and renamed to simply `View`

* Think about a way to allow a `MviView<A,B>.` to be converted to a `MviView<C, D>`
 
This can greatly help reusing views. Each view could declare it's own generics and in each place you want to 
use them you map your current state into the state of this view and also map the view actions into the
 actions in your particular loop. 
 
 * Tests to write:

   * The reducer is called in a thread-safe way. 
   * The actions don't wait for the handler to process them (the view is not blocked if this happens)
     * Either coming from view or external actions
   * If the view takes a long time to render a state and since then 2+ states come through, the view does
    not render intermediary states
   * When the MVFlow object scope is destroyed, everything stops
   * When the View scope is destroyed, the view actions and view updates stop
     

## Android

* ability to suspend state updates when the app is not resumed (or started)
