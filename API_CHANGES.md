# 10.2

## New features
### Analysis and tracking

* Add `analyzeFilesAndTrack` method to `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService` to allow client to submit files for analysis and expect to get a notification with tracked issues.
  * should be called by the client instead of the deprecated `analyzeFiles` method.
  * accepts `AnalyzeFilesAndTrackParams` instead of deprecated `AnalyzeFilesParams`. The difference is that the new `AnalyzeFilesAndTrackParams` expects to get one more flag - `shouldFetchServerIssues`.

* Add `raiseIssues` method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` to report tracked issues.
  * will be called by core after tracking is done.
  * returns `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaiseIssuesParams` that contains new data class `TrackedIssueDto`.
  * `TrackedIssueDto` contains data from matching/tracking with previously analyzed issues. For connected mode - also has data from matched server issues and anticipated issues.

## Deprecation
* `analyzeFiles` method of `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService` should be called. `analyzeFilesAndTrack` should be called instead.
* `trackWithServerIssues` method of `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingRpcService` should be called if the client migrated to a new analysis API. Tracking will be performed by the core internally.
* Implementation of the `didRaiseIssue` method of `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` is no longer required. Instead new method `raiseIssues` should be implemented.
  * Note that the new method `raiseIssues` reports a collection of issues and works like issue publishing. Every call contains the full list of known issues. So it should be published as a whole instead of previously published issues.


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
