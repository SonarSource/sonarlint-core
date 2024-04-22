# 10.1

## Breaking changes

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
