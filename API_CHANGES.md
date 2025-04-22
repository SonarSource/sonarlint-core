# 10.20

## Breaking changes

* Remove `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.UserTokenRpcService` and `org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl.getUserTokenService`. They were not useful anymore.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams` deprecated constructor. Use the other one.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService#checkSmartNotificationsSupported` method and associated parameter and response. They were not useful anymore.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto` deprecated constructor. Use the other one.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto` deprecated constructor. Use the other one.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams` deprecated constructor. Use the other one.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto` deprecated constructors. Use the other one.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto` deprecated constructor and the `getPid` getter. They were not useful anymore.
* Remove the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams` deprecated constructor. Use the other one.

## New features

* Add a new constructor to `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams` constructor to provide flags as an enum values list.

## Deprecation

* Deprecate `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto` class.

# 10.19

## Deprecation

* Deprecate the `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams` constructor, and replace it with a new one without the `startTime` parameter, that is no longer relevant.

# 10.17

## New features

* Add a new constructor to `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto` to accept a map of `SonarCloudRegion` to `SonarQubeCloudRegionDto`.
    *  Per region a `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto` must be provided that contains the *base*, *API* and *WebSocket* URIs.
    * `null` values are accepted for every URI - it will internally fallback to the actual region URIs for a `null` value encountered.

## Deprecation

* Deprecate the `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService.checkSmartNotificationsSupported` method. It always returns that notifications are supported.

# 10.16

## New features

* Introduce a new `org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.AiCodeFixRpcService` class, containing a `suggestFix(SuggestFixParams)` method.
* Introduce a new `isAiCodeFixable` method in `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto`.

## Deprecation

* Deprecate the `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto` constructor. It is replaced by an overload in which the new API base URL should be provided.

# 10.14

## Breaking changes

* Add new method `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#invalidToken` to notify client that WebAPI calls to SQS/SQC fails due to wrong token
    * Client can implement this method to offer user to change credentials for the connection to fix the problem
    * For now notification is being sent only for 401 Unauthorized HTTP response code since it's corresponds to malformed/wrong token and ignores 403 Forbidden response code since it's a user permissions problem that has to be addressed on the server
    * Also once notification sent, backend doesn't attempt to send any requests to server anymore until credentials changed
* Add `region` to `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams` and `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionSuggestionDto` to support multi-region SQC connection configuration
    * Constructor without region parameter is removed

* Removed `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#didRaiseIssue` and associated types. See `raiseIssues` and `raiseHotspots` instead.
* Removed `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer#getIssueTrackingService` and associated types. Tracking is managed by the backend.
* Removed `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer#getSecurityHotspotMatchingService` and associated types. Tracking is managed by the backend.
* Removed `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams#getServerUrl()`. Use `getConnectionParams` instead.
* Removed `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFiles`. Use `analyzeFilesAndTrack` instead.
* Removed deprecated methods in `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto`:
  * `getSeverity`
  * `getType`
  * `getCleanCodeAttribute`
  * `getImpacts`
  * Use `getSeverityMode` instead.
* Removed deprecated methods in `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto`:
  * `getSeverity`
  * `getType`
  * `getCleanCodeAttribute`
  * `getImpacts`
  * Use `getSeverityMode` instead.

## New features

* Add SonarCloud region parameter to 
  * `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams` 
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto`
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto`
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.FuzzySearchUserOrganizationsParams`
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams`
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams` 
  * This is in order to support multi-region SQC connection configuration. Constructors without region parameter are deprecated
* `org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService#newTrace(String, String)` can be used internally
  to initialize a manual trace in Sentry
* When monitoring is enabled, 1% of all analysis requests are sent to Sentry's performance tracing feature 
* Two new system properties can be used to tune the behavior of the Sentry integration:
  * `sonarlint.internal.monitoring.dsn` overrides the default [DSN](https://docs.sentry.io/concepts/key-terms/dsn-explainer/) (e.g. for tests)
  * `sonarlint.internal.monitoring.tracesSampleRate`, parsed as a `java.lang.Double`, overrides the default sampling rate of analysis requests

# 10.13

## Breaking changes

* New feature flag `enableMonitoring` in `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto` allows clients to opt into monitoring with Sentry

## New features

* Introduce `org.sonarsource.sonarlint.core.rpc.protocol.backend.dogfooding.DogfoodingRpcService.isDogfoodingEnvironment` method to allow clients to know if it is running in a dogfooding environment
  * Will return `true` if `SONARSOURCE_DOGFOODING` environment variable is set and equals `"1"`
  * Will return `false` in all other cases
* Introduce opt-in monitoring via Sentry
  * As a first step, the monitoring service is only initialized in dogfooding environments when the feature flag is set
  * All logging events sent to the client at the `ERROR` level are reported as monitoring events

# 10.12

## Breaking changes

* Adapt `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams.languageSpecificRequirements to accept org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.JsTsRequirementsDto` instead of `clientNodeJsPath`

## New features

* Introduce `bundlePath` initialization parameter in `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.JsTsRequirementsDto` to allow clients to provide the path to the unzipped es-lint bridge bundle
  * The path will be passed down to the Js/Ts/CSS analyzer and will indicate that the analyzer does not need to unzip the bundle itself, thus reducing the usage of the `.sonarlint` temporary storage
  * Provide `null` to keep the previous behavior

# 10.11

## Breaking changes

* Signature of `org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams#DidUpdateFileSystemParams` was changed
  * Parameter `addedOrChangedFiles` was split into `addedFiles` and `changedFiles`
* Removed parameter `branch` and `pullRequest` from `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto` as it should not be used anymore by the client.

# 10.10

## New features

* Introduce a new method `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService.shouldUseEnterpriseCSharpAnalyzer` to allow clients to know what kind of C# analyzer should be used for the analysis.
  * The method returns a boolean value indicating whether the enterprise C# analyzer should be used or not
  * The method returns `true` if a binding exists for config scope AND the related connected server has the enterprise C# plugin (`csharpenterprise`) installed
  * The method returns `true` if binding exists with a SonarQube version < 10.8 (i.e. SQ versions that do not include repackaged dotnet analyzer) OR SonarCloud
  * The method returns `false` in standalone mode or if connected to non-commercial edition of SonarQube with a version >= 10.8
* Inject the relevant C# analyzer to analysis engines based on the above and share the path to the analyzer JAR as an analysis property for the OmniSharp plugin.

## Breaking changes

* Add two new constructor arguments to `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.OmnisharpRequirementsDto` for clients to declare the paths to the Open-Source and Enterprise C# analyzers.

# 10.9

## New features

* A new attribute `severityMode` has been added to `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto`
  and `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto` that automatically contains either `StandardModeDetails` or `MQRModeDetails`
  * A new type `StandardModeDetails` has been introduced, which contains information about severity and type
  * A new type `MQRModeDetails` has been introduced, which contains information about clean code attribute and impacts
  * You should display the finding accordingly to the information contained by `severityMode`
* A new method `IssueRpcService#getEffectiveIssueDetails` has been added to the backend to allow clients to retrieve detailed information about an issue
  * The method accepts a configuration scope ID and an issue ID (UUID) as parameters
  * The method returns a `GetEffectiveIssueDetailsResponse` object containing the detailed information about the issue
  * It is preferred to use this method instead of the `RulesRpcService#getEffectiveRuleDetails` when retrieving rule description details in the context of a specific issue, as this new method will provide more precise information based on the issue, like issue impacts & customized issue severity

## Breaking changes

* Remove the `org.sonarsource.sonarlint.core.serverconnection.ServerPathProvider` class.
* Remove `severity` and `type` fields from `org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto` as this class is only used for fetching standalone rule details, which should always have the Clean Code Attribute and Impacts

## Deprecation

* The following attributes have been deprecated from `org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto` and
  `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto`, you should now use the new attribute `severityMode`
  * `severity`
  * `type`
  * `cleanCodeAttribute`
  * `impacts`

# 10.7.1

## Breaking changes

* Add a new method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#matchProjectBranch` allowing the backend to check whether the locally checked-out branch matches a requesting server branch

# 10.7

## Breaking changes

* Signature of `org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate#raiseHotspots` was changed
  * Parameter `issuesByFileUri` has been rightfully replaced by `hotspotsByFileUri`
  * This is purely a naming change, there is no functional impact

## New features

* Add return value `GetForcedNodeJsResponse` to `org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate#didChangeClientNodeJsPath` indicating whether
  the Node.js path is effective or not. If that's the case, the path and the version will be returned. 
  * It's not mandatory to use this return value. It is used by some IDEs to show the current Node.js version used.
* Add a new system property `sonarlint.debug.active.rules` to log active rules in verbose mode when triggering an analysis

# 10.6

## Breaking changes

* Signature of `org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate#noBindingSuggestionFound` was changed
  * Replaced parameter with `org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams`
  * Former parameter `projectKey` can now be accessed by `params.getProjectKey()`
* Removed deprecated constructors from `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams`

## New features

* Add a field to `org.sonarsource.sonarlint.core.rpc.protocol.common.NoBindingSuggestionFoundParams` indicating whether the suggestion where
  no binding was found by is SonarCloud or not, can be used to display a more precise notification in the IDE rather than a generic one
* Add a signature to `SloopLauncher.start`, allowing clients to add custom JVM arguments to the start of the process

# 10.4

## Breaking changes

* Add new `isUserDefined` parameter into `org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto`
  * User-defined files will be included in the analysis. Non-user-defined files such as generated or library files will be excluded from
    analysis when analysis is triggered by the backend. If the analysis was forced by the client, exclusions are not respected.

* Introduce a new parameter in the constructor of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto`: `canOpenFixSuggestion`.
  * This flag lets clients completely disable the opening a fix suggestion in the IDE, which can be useful if the feature is not yet available in the client.
* Introduce a new initialization parameter `TelemetryMigrationDto` to `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams`
  * The parameter is nullable and should be used only by the SLVS to migrate its telemetry. All other clients should provide `null` as a value.

## New features

* Add a method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` to allow the backend to request client-defined file
  exclusions from the client before every standalone analysis.
  * `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#getFileExclusions` to request file exclusions

* Add a field to `org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto` to allow the backend to distinguish non-user-defined
  files to exclude from analysis

* Add `showFixSuggestion` method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient`
  * It's only available when the feature flag `canOpenFixSuggestion` is enabled
  * When using this method, you will receive a single fix suggestion for a specific issue that should be displayed to the user
  * The user should have the possibility to accept or decline the fix suggestion
  * The fix suggestion can be displayed at different locations in the file

* Add `fixSuggestionResolved` method to `org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService`
  * You should use this method whenever a fix suggestion has been accepted or declined
  * If the fix has multiple changes (snippets), you should call the method once for each
  * The `indexSnippet` should be filled if possible, it corresponds to the snippet index in the list of changes
  * If you do not know if the fix was accepted or declined at the snippet level, you should call the method once for the whole fix

### File events

* Add the `didOpenFile` and `didCloseFile` methods to `org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService`.
  * Clients are supposed to call these methods when a file is opened in the editor or closed.

### Analysis

* Add a new constructor in `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams` to let clients provide if automatic analysis is enabled.
* Add a new `didChangeAutomaticAnalysisSetting` method in `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService`
  * Clients are expected to call it whenever users change the "enable automatic analysis" setting.
* Add new methods to `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService` to force analysis
  * `analyzeFullProject` forces analysis all files of the project that was provided to backend by method `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#listFiles`
  * `analyzeFileList` forces analysis for the provided set of files
  * `analyzeOpenFiles` forces analysis of all files that were reported as opened using `org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService#didOpenFile`
  * `analyzeVCSChangedFiles` forces analysis of modified and not committed files

# 10.3.2 

## Breaking changes

* Change `disabledLanguagesForAnalysis` parameter of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams` introduced in 10.3 version to `disabledPluginKeysForAnalysis`
  * Analysis will be disabled for plugins specified in `disabledPluginKeysForAnalysis` but it will be still possible to consume Rule Descriptions
  * Can be null or empty if clients do not wish to disable analysis for any loaded plugin

# 10.3

## Breaking changes

* Add new `disabledLanguagesForAnalysis` parameter into `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams`
  * Analysis will be disabled for languages specified `disabledLanguagesForAnalysis` but it will be still possible to consume Rule Descriptions
  * Can be null or empty if clients do not wish to disable analysis for any loaded plugin

## New features

* Add a method to `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient` to allow backend to request inferred analysis properties from the client before every analysis. It's important because properties may change depending on files being analysed.
  * `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient#getInferredAnalysisProperties` to request inferred properties
* Add a method to the `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService` to let the client notify the backend with user defined analysis properties
  * `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#didSetUserAnalysisProperties` to set user defined properties
* For analysis, both user-defined and inferred properties will be merged. If the same property is inferred by the client and provided by the user - the inferred value will be used for analysis.

### Open Issue in IDE

* Add the `getConnectionParams` method to `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams`
  * It allows clients to get parameters to create either SonarQube or SonarCloud connection
  * This field type is `Either<AssistSonarQubeConnection, AssistSonarCloudConnection>`
  * Common methods of both connection types are added to the `AssistCreatingConnectionParams` class to provide users simplicity

## Deprecation

* Deprecate `isSonarCloud` parameter from `org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams`
  * This value on no longer needed on the backend side.

* `org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams.getServerUrl` is only meaningful for SQ
  connections. Use `getConnection().getLeft().getServerUrl()` instead to get the `serverUrl` of a SQ connection

* The existing constructor in `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams` is now deprecated, the newly added constructor should be used instead (see above).

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

* Add `getRawIssues` method to `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse`
  * It allows clients to get raised issues in the analysis response.
  * This method is temporarily added and will be removed when the deprecated APIs have been dropped.

## Deprecation

* `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFiles` and the underlying DTOs are deprecated, should be replaced by `analyzeFilesAndTrack`.
* As a consequence, `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.didRaiseIssue` and the underlying DTOs are now deprecated. It should be replaced by `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.raiseIssues` and `org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient.raiseHotspots`.
* `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.IssueTrackingRpcService` and the underlying DTOs are deprecated, the functionality is now handled by `analyzeFilesAndTrack`.
* `org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.SecurityHotspotMatchingRpcService` and the underlying DTOs are deprecated, the functionality is now handled by `analyzeFilesAndTrack`.
* `org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine` and all classes from the `sonarlint-java-client-legacy` module are now deprecated. Analysis should happen via `org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack`, and the `sonarlint-java-client-legacy` artifact will soon be removed.
* The `pid` parameter of the `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto` constructor is not used anymore (the backend PID is used instead). The constructor is now deprecated, and a new constructor without this parameter was introduced and should be used. The `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto.getPid` method is not used anymore and also deprecated.

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
