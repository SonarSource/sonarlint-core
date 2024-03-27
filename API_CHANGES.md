# 10.1

Breaking changes:

* Introduce a new parameter in the constructor of `org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto`: `enableTelemetry`.
  * This flag lets clients completely disable the telemetry, which can be useful when using Sloop in the context of tests.
  * The flag replaces the `sonarlint.telemetry.disabled` system property.
  * For clients that want to keep the same behavior, they can read the system property on the client side and pass it to the `FeatureFlagsDto` constructor.
