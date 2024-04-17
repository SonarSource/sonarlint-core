# 10.1

Breaking changes:

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

New features:

* Add methods to `org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService` to track binding creation.
  * `addedManualBindings` should be called by clients when a new binding is created manually.
  * `addedImportedBindings` should be called by clients when a binding is created from a shared connected mode configuration file.
  * `addedAutomaticBindings` should be called by clients when a binding is created using a suggestion from a server analysis settings files.

* Add `isFromSharedConfiguration` field to `org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams` and `org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto`.
  * This field tells the client whether the binding suggestion comes from a shared connected mode configuration file (`true`) or from analysis settings files (`false`).
