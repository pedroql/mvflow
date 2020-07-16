# To do ✔️

In no particular order

## Documentation

* Ensure documenting public methods is required by CI

* Write docs in a github.io page

* Write blog posts

* Simplify readme in main project

## Sample code to write

* Base example (done in readme)

* Android example, using a view model to retain the MVFlow object

* Android example using a recycler view (could be the same as above)

* Android example using live data

* How to use observers

* How to do analytics events

  * When you press a button
  * When a request succeeds or fails

* Example of using a view with its own concept of state and action and then mapping this to two screens which use the 
same view for slightly different things. (This shows a good practice as well as how to map views/actions - 
functionality to be developed)

## To consider

Not sure we will do those things, but keeping track of some possibilities. Feedback welcome!

* Creating a DSL

* Split the View interface into separate interfaces (thanks for the suggestion!)
  
  * This would help with the ability to map View state and actions, in order to enable reuse
  
* The `MviView` interface maybe should be defined inside

* Consider changing from creating a class (MVFLow) to calling a factory or builder

* Reconsider if it is correct to allow to observe actions, mutations, and states

   * Maybe it would be a better alternative to allow the handler to emit new actions at any point 
 
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
 
This can greatly help reusing views. Each view could declare its own generics and in each place you
 want to use them you map your current state into the state of this view and also map the view 
 actions into the actions in your particular loop. 
  
---

* Maybe MVFlow should use a hypervisor scheduler. If one child fails, do we want the others to be
 cancelled? (Maybe this could be a parameter) 
 
* Probably should call `buffer` in the actions so that slow handlers don't block whatever is 
emitting actions

* Tests to write:
   * When the MVFlow object scope is destroyed, everything stops
   * When the View scope is destroyed, the view actions and view updates stop
   * Ensure that (unlike what happened while writing tests) between setting up the view actions and
    subscribing the state to the view, there can't be missed events
   * Test what happens when actions, handler, and reducer throw exceptions
     
## Android

* Ability to suspend state updates when the app is not resumed (or started)

* Integration with `LiveData`
