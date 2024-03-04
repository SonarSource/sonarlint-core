# Manage the HTTPClient in the core

## Why

At the moment, the core declares a Java interface for HttpClient, that should be implemented by each client, and passed to several APIs. It is the responsibility of the client side to implement this interface, by choosing an HTTPClient implementation, and configuring it (SSL, proxy, timeouts, credentials, ...).
The idea is to move the management of this HTTPClient to the core. 

### Decision Drivers

* Consistent behavior for all IDEs (timeouts, cancellation, SSL, security ...)
* More freedom in the core to do more advanced HTTP operations (SSE, websockets, ...) without having to ask each client to implement new code.
* Intensive testing of the HTTPClient for our use cases is easier in SLCORE
* The current API is not RPC-friendly (asking a Java client to implement an interface)

## What

### Considered Options

* keep HTTPClient on IDE side, with an RPC-friendly protocol between client and core
    * Pros:
        * more freedom for each IDE
        * once CORE will be a separate process, the requests will continue to appear as sent by the IDE (maybe important for firewalls)
        * possibly simpler to integrate into IDE user experience regarding HTTP configuration (SSL, proxy, ...)
        * possibility to use an HTTPClient provided by the IDE SDK (but not the case as of today) 
    * Cons:
        * duplicate code in each IDE
        * possibly different behavior
        * need to design an RPC-friendly protocol for core to forward HTTP operations to the client. No easy for persistent connections (SSE, websocket)
        * the core needs its own implementation of an HTTPClient for its medium tests/ITs
* move HTTPClient to the core
    * Pros:
        * less code in each IDE
        * consistent behavior
        * fewer round-trips between client and core
        * all core tests (medium tests/ITs) will use the "production" HTTPClient
    * Cons:
        * need to design an RPC-friendly protocol for core to get credentials, proxy settings, and SSL certificate validation


### Option Retained

We retained "move HTTPClient to the core", mainly for the improved testability, and as it fits better with [ADR-0000](0000-move-more-responsibilities-to-the-core.md) decision.

### Consequences

* For SLVSCODE, there should be almost no impact
* For SLE, it will change the HTTPClient from OkHttp to Apache HTTPClient, so it could have some side effects
* For SLI, this will be the same library, but the integration with IntelliJ SSL and Proxy settings will need to be reworked with care
* The HTTPClient interface will be deprecated, and should ultimately disappear

## How

New requests will be added to the backend protocol:

* `getCredentials` to ask client for credentials of a given connection
* `selectProxies` to ask client for configured proxies for a given URL
* `getProxyAuthentication` to ask client for proxy credentials
* `isServerTrusted` to ask client if a given SSL certificate should be trusted