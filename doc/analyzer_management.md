# Artifact Management in SonarLint Core

This document explains how SQ:IDE manages the artifacts (plugins and plugin dependencies) required
to analyze code. It covers where artifacts come from, how they are loaded, and how the loading
strategy adapts to standalone vs. connected mode.

---

## Glossary

| Term | Meaning |
|------|---------|
| **Plugin** | A standard SonarSource analyzer packaged as a JAR. Loaded by the analysis engine via the plugin API. |
| **Plugin dependency / sidecar** | An artifact required for a plugin to work but not itself a plugin (e.g. an OmniSharp distribution for the C# analyzer). Deployed on binaries.sonarsource.com alongside plugins. |
| **Artifact** | Umbrella term for both plugins and plugin dependencies. |
| **Artifact origin** | Where an artifact physically came from: `EMBEDDED`, `ON_DEMAND`, `SONARQUBE_SERVER`, `SONARQUBE_CLOUD`. Represented by the `ArtifactOrigin` enum. |

---

## Motivation

Historically SQ:IDE shipped every language analyzer bundled inside the IDE extension. This made
the extension large and required a release each time a new analyzer version was needed.

Two changes were made to address this:

1. **On-demand source.** A curated set of artifacts (e.g. CFamily, C# OSS, OmniSharp) can be
   downloaded at runtime from `binaries.sonarsource.com`, reducing the size of the shipped
   extension. This also enables plugin _dependencies_ to be distributed alongside plugins on the
   same infrastructure.

2. **Policy-based loading.** Rather than having a single monolithic resolver, artifact loading is
   now split between *where artifacts come from* (`ArtifactSource`) and *how sources are
   combined* (`ArtifactsLoadingStrategy`).

---

## Core Abstractions

### `ArtifactSource`

Represents **one place where artifacts can be obtained**. Every source exposes two methods:

```
listAvailableArtifacts(Set<SonarLanguage> enabledLanguages) → List<AvailableArtifact>
   Returns all artifacts known to this source for the given set of enabled languages, without triggering any downloads. This is a pure query.
   Implementations should return artifacts corresponding to enabled languages, and artifacts that are not tied to a specific language.

load(String artifactKey) → Optional<ResolvedArtifact>
    Action. Ensures the artifact is available, scheduling a background
    download if needed. Returns empty if this source does not handle
    the given key.
    May return a ResolvedArtifact in DOWNLOADING state.
```

`ResolvedArtifact` captures the outcome: `(ArtifactState state, Path path, ArtifactOrigin origin,
Version version)`. The state is one of: `ACTIVE`, `SYNCED`, `DOWNLOADING`, `FAILED`, `PREMIUM`,
`UNSUPPORTED`.

There are three concrete implementations:

#### `EmbeddedPluginSource`

Backed by JARs physically bundled in the IDE extension. Never triggers downloads. Covers both
language plugins (e.g. `sonar-java-plugin.jar`) and companion plugins embedded by the client
(e.g. `sonarlint-omnisharp-plugin.jar`).

Two factory methods select the right set of paths:
- `EmbeddedPluginSource.forStandalone(params)` — standalone embedded paths + optional C# OSS standalone JAR
- `EmbeddedPluginSource.forConnected(params)` — connected-mode embedded paths only

#### `BinariesArtifactSource`

Backed by `binaries.sonarsource.com`. Handles both **plugins** (CFamily, C# OSS) and **plugin
dependencies** (OmniSharp distributions). Artifacts are cached under
`<storage-root>/ondemand-plugins/`.

- `listAvailableArtifacts(Set<SonarLanguage> enabledLanguages)` lists artifacts available on Binaries.
- `load(key)` checks the cache first; if absent, schedules an async download and returns
  `DOWNLOADING` immediately. A `PluginStatusUpdateEvent` is published when the download finishes
  (with `ACTIVE` or `FAILED`).
- Signature verification (`OnDemandPluginSignatureVerifier`) runs after every download.
- Concurrent duplicate downloads for the same artifact are deduplicated (`UniqueTaskExecutor`).

#### `ServerPluginSource`

Backed by a specific SonarQube Server or SonarQube Cloud connection. One instance per connection,
cached by `ConnectedArtifactsLoadingStrategyFactory`.

- `listAvailableArtifacts(Set<SonarLanguage> enabledLanguages)` returns what the server currently exposes (converted from the server
  plugin list). Falls back to an empty list if the server is unreachable.
- `listServerPlugins()` returns the raw `ServerPlugin` list (richer metadata, used by the loading
  policy for skip-list and companion decisions).
- `load(key)` returns `SYNCED` if the artifact is on disk with a matching hash, or schedules an
  async download (returns `DOWNLOADING`). Enterprise-variant resolution happens here.
- `isAnyDownloadInProgress()` delegates to `ServerPluginDownloader`.

---

### `ArtifactsLoadingStrategy`

Represents **how sources are combined** to produce the full set of resolved artifacts for a given
context (standalone or connected). The interface exposes:

```
resolveArtifacts() → ArtifactsLoadingResult
    Resolves all artifacts from all managed sources, applying priority and
    policy rules. The result holds the resolved artifact map (keyed by
    artifact key) and the set of enabled languages.
    May schedule background downloads.
```

There are two implementations:

#### `StandaloneArtifactsLoadingStrategy`

Used when the user has no server connection. Sources (in ascending priority):

| Priority | Source                                    | Why                                          |
|----------|-------------------------------------------|----------------------------------------------|
| Lowest   | `BinariesArtifactSource`       | Fallback for on-demand artifacts             |
| Highest  | `EmbeddedPluginSource` (standalone paths) | Overrides on-demand when embedded is present |

Languages available only in connected mode are reported as `PREMIUM` (no artifact path, just a
status indicating the language requires a server connection).

Resolution uses a winner-map (ascending priority, last writer wins per key), then one post-processing pass:
- For each `SonarLanguage` whose plugin key is still unresolved and is connected-only: mark as `PREMIUM`.

#### `ConnectedArtifactsLoadingStrategy`

Used when the user has a server connection. One instance per connection, cached by
`ConnectedArtifactsLoadingStrategyFactory`. Sources (in ascending priority):

| Priority | Source | Why |
|----------|--------|-----|
| Lowest | `BinariesArtifactSource` | Fallback for on-demand artifacts |
| Middle | `ServerPluginSource` | Server-specific analyzers override binaries |
| Highest | `EmbeddedPluginSource` (connected paths) | JARs the client always carries (normally win) |

Resolution uses a winner-map (ascending priority, last writer wins per key), then two post-processing passes:
1. **Enterprise-variant deduplication**: when a different-key enterprise variant (e.g. `csharpenterprise`) is present, the base key is removed so both are not loaded simultaneously.
2. **Enterprise priority override**: when the server reports a plugin as enterprise (same-key enterprise editions such as Go or IaC), the server source is forced for that key even if the embedded source would normally win.

---

## Artifact State Machine

```
           load() called
               │
               ▼
       ┌───────────────┐
       │  On disk?     │──── Yes ──► ACTIVE / SYNCED
       └───────────────┘
               │ No
               ▼
       ┌───────────────┐
       │  Schedule     │
       │  download     │──────────► DOWNLOADING
       └───────────────┘
               │ (async)
        ┌──────┴──────┐
        ▼             ▼
      ACTIVE        FAILED
  (event fired)  (event fired)
```

`PluginStatusUpdateEvent` is published on every transition out of `DOWNLOADING`. The IDE listens
to these events to update the status bar.

---

## Step-by-Step Flow

### 1. Initialization

When `SonarLintBackendImpl.initialize()` is called by the IDE extension, the Spring context boots.
`PluginsService`, `BinariesArtifactSource`, and the connected-mode factory are
instantiated as singletons. No artifacts are loaded yet.

### 2. Connection Sync (connected mode only)

If the workspace has bound projects, `PluginsSynchronizer` hits `api/plugins/installed` to learn
what the server exposes. This updates the local storage with expected plugin paths and hashes.

### 3. Artifact Resolution

When analysis is requested (or when the IDE requests plugin statuses), `PluginsService` calls
`resolveArtifacts()` on the appropriate `ArtifactsLoadingStrategy`. The strategy runs its
resolution passes and returns an `ArtifactsLoadingResult`.

Artifacts in `DOWNLOADING` state have a non-null `downloadFuture`. The caller uses
`ArtifactsLoadingResult.whenAllArtifactsDownloaded()` to run a callback (publishing a
`PluginsSynchronizedEvent`) once all pending downloads complete.

### 4. Background Download

When `load()` on a source cannot find the artifact locally, it enqueues a background download:
- `BinariesArtifactSource` uses `UniqueTaskExecutor` + HTTP fetch from
  `binaries.sonarsource.com`, followed by signature verification.
- `ServerPluginSource` delegates to `ServerPluginDownloader`, which deduplicates concurrent
  downloads for the same connection.

### 5. Class Loading

Once all required artifacts are in `ACTIVE` or `SYNCED` state, `PluginsService` passes the
resolved JAR paths to the core `PluginsLoader`, creating isolated `ClassLoader` instances per
plugin. Analysis can then run.

---
