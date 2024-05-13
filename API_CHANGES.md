# 10.2

## Breaking changes

* `org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate#didDetectSecret` had no `configScopeId` parameter, it was added

## New features

### Analysis and tracking

* Add the `analyzeFilesAndTrack` method to `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService`.
  * It allows clients to submit files for analysis, let the backend deal with issue tracking, and will lead to a later notification via `raiseIssues` and `raiseHotspots` (see below).
  * Usages of `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFiles` should be replaced by this new method.
  * It accepts a `AnalyzeFilesAndTrackParams` object instead of the deprecated `AnalyzeFilesParams`. The extra flag `shouldFetchServerIssues` should be set to `true` when the analysis is triggered in response to a file open event.
  * When using this method, implementation of the `didRaiseIssue` method of `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` is no longer required. The new `raiseIssues` and `raiseHotspots` methods should be implemented instead (see below).

* Add `raiseIssues` method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` to report tracked issues.
  * Will be called by the backend when issues should be raised to the user. The UI should be updated accordingly.
  * The `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams` class contains a list of issues to raise by file URI.
  * Each raised issue went through issue tracking, and has potentially been matched with a previously known issue and/or a server issue in connected mode.
  * This new method reports a collection of issues replacing the ones previously raised. Every call contains the full list of known issues.

* Add `raiseHotspots` method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` to report tracked Security Hotspots.
  * Will be called by the backend when hotspots should be raised to the user. The UI should be updated accordingly.
  * The `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseHotspotsParams` class contains a list of hotspots to raise by file URI.
  * Each raised hotspot went through hotspot tracking, and has potentially been matched with a previously known hotspot and/or a server hotspot in connected mode.
  * This new method reports a collection of hotspots replacing the ones previously raised. Every call contains the full list of known hotspots.

## Deprecation

* `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFiles` and the underlying DTOs are deprecated, should be replaced by `analyzeFilesAndTrack`.
* As a consequence, `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.didRaiseIssue` and the underlying DTOs are now deprecated. It should be replaced by `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.raiseIssues` and `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.raiseHotspots`.
* `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingRpcService` and the underlying DTOs are deprecated, the functionality is now handled by `analyzeFilesAndTrack`.
* `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingRpcService` and the underlying DTOs are deprecated, the functionality is now handled by `analyzeFilesAndTrack`.
* `org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine` is now deprecated. Analysis should happen via `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack`.

# 10.1

## Breaking changes

* Replace the last constructor parameter of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams`.
  * Clients should provide an instance of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements`.
  * The previous Node.js path parameter is now part of this new `LanguageSpecificRequirements`, together with configuration related to Omnisharp.
  * For clients not executing analysis via the backend, or not supporting C#, a `null` value can be passes as the 2nd parameter of the `LanguageSpecificRequirements` constructor

* Introduce a new parameter in the constructor of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto`.
  * Clients should provide the PID of the host process.
  * For clients not executing analysis via the backend, this parameter is not used, so a dummy value can be provided.

* Introduce a new parameter in the constructor of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto`: `enableTelemetry`.
  * This flag lets clients completely disable the telemetry, which can be useful when using Sloop in the context of tests.
  * The flag replaces the `sonarlint.telemetry.disabled` system property.
  * For clients that want to keep the same behavior, they can read the system property on the client side and pass it to the `FeatureFlagsDto` constructor.

* Stop leaking LSP4J types in API (SLCORE-663) and wrap them in SonarLint classes instead
  * `org.eclipse.lsp4j.jsonrpc.messages.Either` replaced by `org.sonarsource.sonarlint.core.rpc.protocol.common.Either`
  * `org.eclipse.lsp4j.jsonrpc.CancelChecker` replaced by `org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker`

* Add new client method `org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate#suggestConnection`.
  * This method is used when binding settings are found for an unknown connection.
  * Clients are expected to notify users about these.

* Move class `org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken.RevokeTokenParams` to `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.RevokeTokenParams`.

* Introduce a new parameter in the constructor of `org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto`.
  * Clients can provide a detected language for the file. This is the opportunity to rely on the IDE's detected type.
  * This is used for analysis, clients can pass `null` to keep the same behavior as before, or if no language was detected.

## New features

### Connected mode: sharing setup

* Add methods to `org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService` to track binding creation.
  * `addedManualBindings` should be called by clients when a new binding is created manually.
  * `addedImportedBindings` should be called by clients when a binding is created from a shared connected mode configuration file.
  * `addedAutomaticBindings` should be called by clients when a binding is created using a suggestion from a server analysis settings files.

* Add `isFromSharedConfiguration` field to `org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams` and `org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto`.
  * This field tells the client whether the binding suggestion comes from a shared connected mode configuration file (`true`) or from analysis settings files (`false`).

### Analysis

* Add the `analyzeFiles` method in `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService`.
  * Clients can use this method to trigger an analysis. It's a request, so they can get a response with details about the analysis.
  * They need to pass the list of URIs representing files to analyze.
  * They also need to pass an "analysis ID", which is a unique ID used for correlating the analysis and issues that are raised via `didRaiseIssue` (see below).

* Add the `didRaiseIssue` method in `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`.
  * This lets clients be informed when an issue is detected during analysis.
  * They can do local tracking or stream the issue to the users.
  * They can retrieve which analysis lead to this issue being raised with the "analysis ID" correlation ID.

* Add the `didSkipLoadingPlugin` method in `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`.
  * This is called after an analysis when a plugin was not loaded.
  * Clients are expected to notify users about these.

* Add the `didDetectSecret` method in `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`.
  * This is called after an analysis when a secret was detected in one of the analyzed files.
  * Clients are expected to notify users about these.
  * The backend does not keep track of any notification regarding secrets detection. Clients will need to manage some cache to avoid notifying users too often.

* Add the `promoteExtraEnabledLanguagesInConnectedMode` method in `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`.
  * This is called after an analysis in standalone mode when a language enabled only in connected mode was detected.
  * Clients are expected to notify users about these.
  * The backend does not keep track of any notification regarding this promotion. Clients will need to manage some cache to avoid notifying users too often.

* Add the `getBaseDir` method in `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`.
  * This is called during an analysis to determine the base directory for the files being analyzed.
  * Clients are expected to implement this request if they support analysis via the backend.
