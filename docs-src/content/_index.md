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

### Coroutines and flows are the answer


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


## Inspiration

Unsurprisingly this library got a lot of inspiration from other libraries. We would like to thank:

* [Orbit MVI](https://github.com/babylonhealth/orbit-mvi/)
* [MVICore](https://badoo.github.io/MVICore/)
* [Knot](https://github.com/beworker/knot)

And everyone who contributed towards those libraries (and their respective inspirations).
