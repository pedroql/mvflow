# MVFlow
**Simple Android* MVI architecture using kotlin flows**

 _\* The architecture is platform-neutral but was designed with Android in mind_

[![CI status](https://github.com/pedroql/mvflow/workflows/Build%20project/badge.svg?branch=master)](https://github.com/pedroql/mvflow/actions?query=workflow%3A%22Build+project%22+branch%3Amaster) 
[![CI status](https://github.com/pedroql/mvflow/workflows/Build%20and%20prepare%20release/badge.svg)](https://github.com/pedroql/mvflow/actions?query=workflow%3A%22Build+and+prepare+release%22)
[![Download ](https://api.bintray.com/packages/pedroql/MVFlow/mvflow-core/images/download.svg?version=0.0.3)](https://bintray.com/pedroql/MVFlow/mvflow-core/_latestVersion) 

Check [our website](https://pedroql.github.io/mvflow/) for all information about the library! 

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

### Coroutines and flows as powerful abstractions 

We believe that coroutines and flows are extremely powerful concepts that can be applied to the MVI architecture. 
They enable us to build a very powerful API with a small and simple surface.

Here are some advantages that they bring:

* Coroutines make asynchronous calls very simple to write and easy to reason about;

* Flows are a great abstraction to represent user events (clicks) and updates from background work;

* Coroutine scopes make handling the lifecycle of requests very simple

## API

You can find a guide to this library on [MVFlow's website](https://pedroql.github.io/mvflow/).

If you would like to take a look behind the scenes, 
[MVFlow.kt](https://github.com/pedroql/mvflow/blob/master/mvflow-core/src/main/kotlin/net/pedroloureiro/mvflow/MVFlow.kt)
contains all the logic in this library. PRs and feedback welcome!

You can also browse the code of the 
[sample Android app](https://github.com/pedroql/mvflow/tree/master/samples/android-app). 


## Inspiration

This library got a lot of inspiration from other libraries. We would like to thank:

* [Orbit MVI](https://github.com/babylonhealth/orbit-mvi/)
* [MVICore](https://badoo.github.io/MVICore/)
* [Knot](https://github.com/beworker/knot)

And everyone who contributed towards those libraries (and their respective inspirations).
