## API changes

This library is yet to get to a stable 1.x release. In this page we will list some parts of that API that we expect
to change soon:

* The way `MVFlow.takeView` works will change to give more control to the consumer of the library with regards to what
coroutine delivers the updates and its scope. (This will affect a big part of the contract of `MviView` too.)

* Observers (`MVFlow.observe(Actions|Mutations|State)`) might be removed from the API in favour of a new "External 
Event" generic class. This would allow the handler to emit events for things that need to happen outside it such as
navigation, showing toasts, etc. At the same time, this might be optional so the current `Handler` typealias might 
remain in place

* `MviView` *might* be renamed `View` and moved inside `MVFlow` class

* `MVFlow` *might* be turned to an interface. If we do this we can create a factory method so it's possible that it 
doesn't change the API contract (TBD).
