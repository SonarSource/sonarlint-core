# Analysis engine concurrent behavior

## Why

The analysis engine is a key component of SonarLint core. It is responsible to execute analyzers. Analyzers may be long operations,
and IDEs may try to run multiple analysis in parallel. Analysis can be cancelled for various reasons. The engine should be fast to react
to shutdown, to not prevent the IDE to stop quickly.
The IDE should not be blocked by the analysis engine.

### Decision Drivers

* analysis may be long operations
* starting/stopping containers may be long operations (we don't control what analyzers do in the start/stop of Startable components)
* we should avoid interrupting threads, to avoid corrupted state
* we should favor cooperative cancellation

## What

### Considered Options

* synchronous operations (start, register module, analyze, unregister module, stop, ...)
  * Pros:
    * simple for backend implementation
  * Cons:
    * blocking for the IDE
    * the IDE is responsible to guarantee the execution order (not call analyze before module registration is completed)
* asynchronous operations
  * Pros:
    * simple for IDE implementation
  * Cons:
    * need to guarantee thread safety and execution order in the backend

### Option Retained

We retained "asynchronous operations", because our goal is always to make implementation on IDE side simpler, and this is more RPC-friendly.

### Consequences

We have to put special effort to guarantee thread safety and execution order.

## How

The first attempt was to use a single command blocking queue, consumed by a single thread. This would guarantee execution order, but this is also preventing us to precisely cancel analysis tasks of a module when it is unregistered. We should maybe have a way to group analysis tasks per module, and make register/unregister module operations specific methods of the analysis engine, instead of mixing them with other commands.

