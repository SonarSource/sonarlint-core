# Implement binding suggestions in the core

## Why

Following [ADR-0000](0000-move-more-responsibilities-to-the-core.md) decision, at the time of
implementing [MMF-2895](https://sonarsource.atlassian.net/browse/MMF-2895), we wondered if we should implement most of
the feature in the core. A first implementation was made in SLVSCODE
for [MMF-1431](https://sonarsource.atlassian.net/browse/MMF-1431) and the question of having a common implementation was
raised.

### Decision Drivers

* Same drivers as [ADR-0000](0000-move-more-responsibilities-to-the-core.md)

## What

### Considered Options

1. Implement the feature in SLI
    * Pros:
        * quicker
    * Cons:
        * again logic duplicated in clients
2. Implement the feature in SLCORE and let it handle as much as possible of the feature
    * Pros:
        * no duplicated logic
    * Cons:
        * slower as the core will need to access more data (connections, projects)

### Option Retained

Even if the development would be slower we decided to go with option 2. We see it as an investment for the future. It
also
goes in the right direction of having more responsibilities in the core, to benefit all clients.

### Consequences

* Consequence 1: To be able to make suggestions the CORE will need to know about connections and about projects
* Consequence 2: The suggestion algorithm might differ from the one used in SLVSCODE

## How

Some other technical decisions that were made:

* Provide a `SonarLintBackend` interface as the entry point for clients. A single entrypoint is easier to use
* Expose a `SonarLintClient` interface that should be implemented by clients to access some data that will remain their
  responsibility. A single entrypoint is easier to implement
* The backend is to be used as a library (in-process), running it out-of-process will be part of another effort
* The backend relies on `lsp4j`, which prepares the ground for moving
  out-of-process later. Also, the interfaces are designed to supported asynchronous messaging by
  returning `CompletableFuture`s

Decisions related to the `SonarLintBackend`:

* only expose the `ConnectionService` and `ConfigurationService` services. More services could come later but were not
  needed for this feature
* those services are designed to be independent
* it is the client's responsibility to store connections' data. It knows better how to store it (e.g. password safe)
* the backend does not store on disk data under the client's responsibility. Connections and configurations are only
  kept in memory
* they are backed by repositories, `ConfigurationRepository` and `ConnectionConfigurationRepository` that hold the data
  in-memory. Repositories can be used to write (usually from services) and read (usually from event handlers)
* an `EventBus` has been implemented that let services raise events about things that happened. That also improves
  decoupling
* methods used to communicate with the backend should use `DTO`s to transfer data

Decisions related to the `SonarLintClient`:

* expose a method to get the `HttpClient`. This wouldn't work by going out-of-process, it is a shortcut taken to not
  lose time and delay the decision about whose responsibility it is to manage HTTP requests (we let it in clients for
  now)
* it is also the client's responsibility for now to access files of the project. This responsibility might be moved to
  the CORE at some point. For now clients are expected to implement `findFileByNamesInScope`, taking a list of filenames
  to find to reduce back and forth
* let the client display suggestions as they see fit. To let them choose the better UX, suggestions for all projects are
  delivered at the same time. This might be more adapted for SLE, where there is a single window for all projects

Decisions related to the `ConnectionService`:

* An `initialize` method, to know what connections were already previously added by the user. We want to distinguish
  already created connections and newly added ones: we don't want to trigger the same processing
* when adding `ConfigurationScope`s several can be provided at the same time. This lets the backend group some
  processing, e.g. binding suggestions to notify the client a single time with suggestions for all projects added
* we have one method per connection event: `didAddConnection`, `didRemoveConnection` and `didUpdateConnection`. At some
  point it could become a single replace instead, if it is more practical for clients
* SonarQube and SonarCloud connections have been separated in 2 different objects, because they don't have exactly the
  same data. It implies a bit of effort on the client side to make the right transformation. We can still re-evaluate
  later

Decisions related to the `ConfigurationService`:

* no `initialize` method, it does not make sense to register `ConfigurationScope`s that are opened before starting the
  backend. We suppose we will have the same processing for already opened scopes and new ones
* when adding `ConfigurationScope`s several can be provided at the same time
  in `ConfigurationService.configurationScopesAdded`. This lets the backend group some
  processing, e.g. binding suggestions to notify the client a single time with suggestions for all projects added

Decisions related to the `EventBus`:

* events are dispatched and handled asynchronously. We don't want to delay the client while handling them
* the bus is backed by a single thread executor service. Only one event can be handled at a given time. We think this
  will help reduce race conditions, and it is simpler to reason about
* as handling an event happens asynchronously, handlers should re-check if the conditions of the event are still valid
* if event handlers have long processing they should move to a different thread, by being careful about race conditions

## More Information

Those decisions were taken in the context of implementing binding suggestions in SLI and trying to keep other IDEs in
mind. The API is young and will very probably continue to evolve and break in the beginning
