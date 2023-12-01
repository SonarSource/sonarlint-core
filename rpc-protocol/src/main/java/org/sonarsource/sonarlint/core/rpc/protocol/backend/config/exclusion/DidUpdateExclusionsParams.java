package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.exclusion;

public class DidUpdateExclusionsParams {

  private final String configScopeId;

  private final ExclusionConfigurationDto exclusions;

  public DidUpdateExclusionsParams(String configScopeId, ExclusionConfigurationDto exclusions) {
    this.configScopeId = configScopeId;
    this.exclusions = exclusions;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public ExclusionConfigurationDto getExclusions() {
    return exclusions;
  }
}
