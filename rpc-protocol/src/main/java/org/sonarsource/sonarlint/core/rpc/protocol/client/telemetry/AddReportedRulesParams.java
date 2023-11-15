package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

import java.util.Set;

public class AddReportedRulesParams {
  private final Set<String> ruleKeys;

  public AddReportedRulesParams(Set<String> ruleKeys) {
    this.ruleKeys = ruleKeys;
  }

  public Set<String> getRuleKeys() {
    return ruleKeys;
  }
}
