package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

public class CheckDependencyRiskSupportedParams {

  private final String configurationScopeId;

  public CheckDependencyRiskSupportedParams(String configurationScopeId) {
    this.configurationScopeId = configurationScopeId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

}
