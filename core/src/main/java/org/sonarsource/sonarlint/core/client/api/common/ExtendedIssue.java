package org.sonarsource.sonarlint.core.client.api.common;

import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public class ExtendedIssue extends Issue {

  private final String severity;
  private final String type;

  public ExtendedIssue(Issue wrapped, SonarLintRuleDefinition sonarLintRuleDefinition) {
    super(wrapped);
    this.severity = sonarLintRuleDefinition.getSeverity();
    this.type = sonarLintRuleDefinition.getType();
  }

  public String getSeverity() {
    return severity;
  }

  public String getType() {
    return type;
  }

}
