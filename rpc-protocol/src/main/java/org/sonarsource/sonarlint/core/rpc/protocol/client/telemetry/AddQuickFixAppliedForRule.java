package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

public class AddQuickFixAppliedForRule {
  private final String ruleKey;

  public AddQuickFixAppliedForRule(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  public String getRuleKey() {
    return ruleKey;
  }
}
