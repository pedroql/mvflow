# To do ✔️

In no particular order

## Documentation

* make sure CI forces the documentation and the website to always be up to date

  * call hugo on CI https://github.com/marketplace/actions/hugo-build

  * see if this error message might affect results: Couldn't find 
    InboundExternalLinkResolutionService(format = `kotlin-website`) for 
    ExternalDocumentationLinkImpl(url=https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/, packageListUrl=https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/package-list),
    using Dokka default
    
  * For some reason dokka is changing the styles.css file which means that CI can't verify if your remaining kdoc is up to date

* Ensure documenting public methods is required by CI (needs to wait for version 0.11 of plugin 
[dokka/pull/955](https://github.com/Kotlin/dokka/pull/955) )

* Write docs in a github.io page
    
  * Cleanup the theme code
         
     * check if the structure of single page, list page, and baseof are well separated
               
     * remove the concept of content id 
          
     * Maybe I can drop the use of responsive grid from pure css?

     * Header partial should be moved from theme to the site
  
  * [HOMEPAGE] Put TOC inline with the content when on landscape
  
  * Create one page alone for the API
    
  * The homepage should contain links to the main sections (just like the header )
  
  * Add mention of the unit tests in the sample project to the docs
    
* Write blog posts

* Write a page with "recipes" pointing certain problems to the sample projects I am writing

* Write a few examples of good patterns I found

  * For example, having one class with a kotlin object to define the State, Action, and Mutation classes. 
  Possibly the reducer and Handler implementations too.
  
  * Using static imports so you don't write `SomethingMVFlow.Action` (or State or Mutation) everywhere
  
  * How to deal with the initial load of data when you open a screen
  
  * Handling process death (and orientation changes) 

## Sample code to write

* Android example using a recycler view (could be the same as above)

* Using external effects

* How to do analytics events

  * When you press a button
  * When a request succeeds or fails

* Example of using a view with its own concept of state and action and then mapping this to two screens which use the 
same view for slightly different things. (This shows a good practice as well as how to map views/actions - 
functionality to be developed)

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
