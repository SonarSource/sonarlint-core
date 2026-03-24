# Analyzer Management in SonarLint Core

This document outlines how SQ:IDE manages the various language analyzers (plugins) required to detect code issues and provide the respective language support.

## Overview

Analyzers are loaded into the analyzer engine to execute rules against source code. Their resolution (where they come from, how they are stored, and when they are updated) is governed by multiple **Artifact Resolvers**, each tailored for a specific mode or source location.

The central interface is `ArtifactResolver` (or `CompanionPluginResolver` for non-language tools). 

### Standalone vs. Connected Environments
Depending on whether a user has bound their project to SQS / SQC, SQ:IDE will query the corresponding artifact resolver.

---

## The Resolvers

### 1. Embedded Artifact Resolvers
Used primarily in Standalone mode, or as fallbacks.
* **`EmbeddedArtifactResolver`**: Responsible for locating and resolving the bundled jar plugins that come directly shipped with SQ:IDE (or the IDE extension).
* **`EmbeddedExtraArtifactResolver`**: Deals with extra necessary files/binaries required by some plugins (for example, the Omnisharp distribution required by the C# analyzer). These do not follow the usual plugin jars packaging but are bundled extra payload.

### 2. Connected Mode Artifact Resolvers
When bound to a Server, SQ:IDE relies on the analyzers configured on this specific server to rule out discrepancies between the IDE and the CI.
* **`ConnectedModeArtifactResolver`**: Handles the resolution of language plugins. It checks if a required plugin is available locally. If not (or if it's outdated compared to the server version), it delegates the downloading out. During the download, it returns a `DOWNLOADING` state, which defers the analysis engine initialization.
* **`ConnectedModeCompanionPluginResolver`**: Handles companion plugins. Non-language tools aren't standard analyzers but provide additional features (e.g. `javasymbolicexecution`, custom data). They behave similarly towards synchronization but target the `CompanionPluginResolver` interface to distinct themselves.
* **`ServerPluginDownloader`**: An orchestrator called by both Connected Mode resolvers. It provides the mechanism to deduplicate concurrent downloads, pull jars from the server via HTTPS safely, and fires asynchronous `PluginStatusUpdateEvent` letting the rest of the application know when a download finishes.

### 3. On-Demand Artifact Resolvers
Some analyzers (e.g., C/C++ or older weighty models) are not bundled by default to save storage or bandwidth, but instead fetched dynamically.
* **`OnDemandArtifactResolver`**: Responsible for downloading these on-demand plugins from a central public artifact repository via HTTP. It uses signature verification (`OnDemandPluginSignatureVerifier`) and deduplicated caching (`OnDemandPluginCacheManager`) to store them efficiently in the local `ondemand-plugins` cache directory.

### 4. Guard & Safety Resolvers
There are specific resolvers to gracefully handle impossible resolutions for a user.
* **`UnsupportedArtifactResolver`**: Blocks and explains why an analyzer shouldn't be loaded (e.g., version mismatch, architecture not supported).
* **`PremiumArtifactResolver`**: Blocks analyzer resolution if a premium feature is requested but the user's current context does not entitle them to it.

---

## Plugin State Transition & Events
Since plugins often need to be downloaded over the network, SQ:IDE employs a state-machine-like approach via `ArtifactState`:
1. **`DOWNLOADING`**: A resolver triggered the download, it's currently running in background.
2. **`ACTIVE` / `SYNCED`**: The artifact is present locally, its checksum matches, and it is ready to be loaded by the engine.
3. **`FAILED`**: The download failed, or the signature couldn't be verified.
An event `PluginStatusUpdateEvent` is published when the state changes to notify UI layers (like the IDE status bar plugin loading panel) or the analysis engine to re-try loading.

---

## Step-by-Step Flow (When `slcore` starts)

To fully understand the plugin and analyzer lifecycle when SQ:IDE starts, let's step through the flow chronologically.

### 1. Spring Context Initialization
When the `SonarLintBackendImpl.initialize()` is called by the IDE extension over RPC, SLCORE boots up its Spring App context. Singletons like `PluginsService` and various storage/configuration services are instantiated. 
*At this point plugins are NOT yet loaded or synced into memory.*

### 2. Connection Sync Trigger
If the user's workspace contains tied/bound projects, the backend orchestrator asks the `PluginsSynchronizer` to hit `api/plugins/installed` and verify which plugins the remote SQS/SQC server operates. This updates the local storage cache with the expected plugin paths/keys.

### 3. Engine Invocation & Lazy Plugin Discovery
Either immediately requested for UI status reports (by the IDE), or when an Analysis actually needs to run, `PluginsService.getPlugins(connectionId)` or `PluginsService.getEmbeddedPlugins()` is triggered.
This is the **critical moment** where resolution begins:
* `PluginsService` aggregates what analyzers are strictly required based on the user's enabled languages configured by the LanguageServer/IDE properties.
* The collection of `ArtifactResolver` implementations evaluates whether they can provide the analyzers for those languages.

### 4. Background Downloading (If Missed)
If the resolvers determine an artifact is required but not yet installed (e.g., `ConnectedModeArtifactResolver` notices a missing required server plugin, or `OnDemandArtifactResolver` discovers a missed heavily C/C++ parser payload):
* A background download executor fires up, pulling the Plugin JAR bytes from the server (or SonarSource CDN).
* The resolver immediately yields an `ArtifactState.DOWNLOADING` status back to the caller. The IDE handles this gracefully by showing a loading spinner on the relevant plugins.

### 5. Resolution & Event Dispatching
Once the download is fully complete:
* Signature verification happens if necessary (like in `OnDemandPluginSignatureVerifier`).
* The resolver posts a `PluginStatusUpdateEvent(ACTIVE / SYNCED / FAILED)`.
* This event circles back to the `PluginStatusNotifierService`, shooting an asynchronous notification to the IDE front-end. The spinner becomes a checkmark.

### 6. Actual ClassLoading
With everything securely cached and checked in the local storage, `PluginsService` feeds the actual `Set<Path>` of `.jar` files to the core `PluginsLoader` inside SLCORE, creating isolated ClassLoaders. Now `slcore` is fully armed and the languages' Sensor analysis is technically capable of running on the codebase.
