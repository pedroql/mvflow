# To do ✔️

In no particular order

## Documentation

* Ensure documenting public methods is required by CI (needs to wait for version 0.11 of plugin 
[dokka/pull/955](https://github.com/Kotlin/dokka/pull/955) )

* Write docs in a github.io page

  * Write basics of MVI and links to other beginner topics
  
  * Add some analytics (?)
  
  * Announce it!

  * Put TOC inline with the content when on landscape
  
  * Optimize for SEO, run checks
  
  * See if I could integrate SASS / minify CSS/js

* Write blog posts

* Simplify readme in main project

* Write a page with "recipes" pointing certain problems to the sample projects I am writing

* Could write a few examples of good patterns I found

  * For example, having one class with a kotlin object to define the State, Action, Mutation, and Handler classes
  
  * Using static imports so you don't write `SomethingMVFlow.Action` (or State or Mutation) everywhere

## Sample code to write

* Android example using a recycler view (could be the same as above)

* Using external effects

* How to do analytics events

  * When you press a button
  * When a request succeeds or fails

* Example of using a view with its own concept of state and action and then mapping this to two screens which use the 
same view for slightly different things. (This shows a good practice as well as how to map views/actions - 
functionality to be developed)

* Update the example using `launchWhenResumed` to use the new better approach

* run https://github.com/plnice/can-i-drop-jetifier to see if can drop jetifier

## To consider

Not sure we will do those things, but keeping track of some possibilities. Feedback welcome!

* Creating a DSL

* Split the View interface into separate interfaces (thanks for the suggestion!)
  
  * This would help with the ability to map View state and actions, in order to enable reuse
  
* Should the external effect channel be conflated? Should it be a broadcast channel (which doesn't start storing values 
until the first observer subscribes)? Should it be a plain Channel which is not shareable?
   
## Core

* Think about a way to allow a `View<A,B>.` to be converted to a `View<C, D>`
 
This can greatly help reusing views. Each view could declare its own generics and in each place you
 want to use them you map your current state into the state of this view and also map the view 
 actions into the actions in your particular loop. 
  
---
 
* Probably should call `buffer` in the actions so that slow handlers don't block whatever is 
emitting actions

* Tests to write:
   * When the MVFlow object scope is destroyed, everything stops
   * When the View scope is destroyed, the view actions and view updates stop
   * Ensure that (unlike what happened while writing tests) between setting up the view actions and
    subscribing the state to the view, there can't be missed events
   * Test what happens when actions, handler, and reducer throw exceptions
   * should review unit tests that verify the view scope going away. They were written just by cancelling the job that
   receives the updates, but maybe we need to create a coroutine context for the view and actually cancel that
   * Write tests for the behaviour when the mvflow scope is cancelled
   * Write unit test to ensure that external actions and actions from view are serialised when
   updating the state.
   * Spend more time trying to make sure the test ```fun `reducer calls are serialized`()``` actually fails when you 
   remove the Mutex 
