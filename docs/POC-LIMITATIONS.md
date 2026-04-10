# SonarLint Architecture POC — Limitations

This document is a quick-reference summary. See [design.md](design.md) for full context and rationale.

**This version is NOT intended for release.**

## Bypassed Checks

- **License check removed** — `LicensedPluginRegistration` removed from `ArchitecturePlugin.define()`. No license server is available in SonarLint.
- **Feature flags unconditionally enabled** — `AnalysisFeatures.enableAllForPoc()` enables all base, advanced, and smells features. Triggered by the presence of `sonar.architecture.udg.dir` in the analysis configuration. Grep for `enableAllForPoc` to find and remove post-POC.
- **SonarLint detection via property presence** — The presence of `sonar.architecture.udg.dir` is used as a proxy for "running in SonarLint". This conflates path configuration with environment detection. Post-POC, replace with proper feature flag resolution via `sonar.architecture.enable` / `sonar.architecture.preview.available` (Roadmap Item 3).
- **`requirePlugins: license:3.4`** remains in `plugin/pom.xml` — SonarLint ignores this manifest entry.

## Scope

- **Only Standalone Mode** — Connected Mode (SonarQube Server / SonarCloud) is not supported.
- **Only Java frontend adapted** — other language frontends (JS/TS, C#, Python) are unchanged and will not produce UDGs in SonarLint.
- **Intended Architecture Model** requires manual configuration (`sonar.architecture.config.model.file` analysis property) — no UI for model management.

## Intended model & module / namespace qualifiers

- **SonarQube / Maven:** Java UDG qualifiers typically include the project (or module) key, e.g. `TreeOfLife:com.animal.bird`. Intended models use matching patterns such as `TreeOfLife:com.animal.bird.**`.
- **SonarLint standalone:** `sonar.moduleKey` is usually **not** set, so the Java frontend does **not** prepend a module prefix — qualifiers are plain package names, e.g. `com.animal.bird` (confirmed by inspecting generated UDGs).
- **Implication:** An intended model built for the server will **not** match SonarLint UDGs unless patterns (and possibly group hierarchy) are adapted to this difference.
- **POC:** Acceptable to use a **patched** model file via `sonar.architecture.config.model.file` (e.g. unprefixed patterns, flattened groups without a top-level project node).
- **Post-POC:** When the model is expected to come **unmodified** from the server (or to be shared between SonarQube and SonarLint), the team must **solve the qualifier mismatch** — e.g. normalize patterns at load time, align UDG keying with server rules, or another agreed strategy. Not in scope for this POC.

## UDG Lifecycle

- **UDG exchange via analysis property** — `sonar.architecture.udg.dir` is set by sonarlint-core (`AnalysisSchedulerCache`), not by plugin code. This follows the established pattern (`sonar.nodejs.executable`, `sonar.cs.internal.analyzerPath`) but introduces coupling between sonarlint-core and the architecture plugin.
- **Stale UDGs after branch switch** — The UDG directory is cleaned at scheduler creation and shutdown, but not on git branch switches. After switching branches, the user must trigger a full re-analysis to regenerate UDGs.
- **DI approach abandoned** — The conceptually cleaner DI-based approach (`UdgWorkDirProvider` as injectable Startable) was attempted first but failed due to cross-plugin DI boundary issues in SonarLint's `ClassDerivedBeanDefinition`. See Approach 3 in [udg-management.md](udg-management.md). Could be revisited post-POC if the DI issues are resolved upstream.

## Other

- Architecture telemetry (`TelemetryCollector` / `TelemetrySensor`) behavior in SonarLint is undefined.
