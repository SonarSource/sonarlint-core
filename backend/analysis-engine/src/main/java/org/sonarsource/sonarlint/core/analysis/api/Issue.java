/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.analysis.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

public class Issue implements IssueLocation {
  private final String primaryMessage;
  private final ClientInputFile clientInputFile;
  private final List<Flow> flows;
  private final List<QuickFix> quickFixes;
  private final Optional<String> ruleDescriptionContextKey;
  private final TextRange textRange;
  private final ActiveRule activeRule;
  private final Map<SoftwareQuality, ImpactSeverity> overriddenImpacts;

  public Issue(ActiveRule activeRule, @Nullable String primaryMessage, Map<SoftwareQuality, ImpactSeverity> overriddenImpacts, @Nullable org.sonar.api.batch.fs.TextRange textRange,
    @Nullable ClientInputFile clientInputFile, List<Flow> flows, List<QuickFix> quickFixes, Optional<String> ruleDescriptionContextKey) {
    this.activeRule = activeRule;
    this.overriddenImpacts = overriddenImpacts;
    this.textRange = Optional.ofNullable(textRange).map(WithTextRange::convert).orElse(null);
    this.primaryMessage = primaryMessage;
    this.clientInputFile = clientInputFile;
    this.flows = flows;
    this.quickFixes = quickFixes;
    this.ruleDescriptionContextKey = ruleDescriptionContextKey;
  }

  public ActiveRule getActiveRule() {
    return activeRule;
  }

  public RuleKey getRuleKey() {
    return activeRule.ruleKey();
  }

  @Override
  public String getMessage() {
    return primaryMessage;
  }

  @Override
  @CheckForNull
  public ClientInputFile getInputFile() {
    return clientInputFile;
  }

  public List<Flow> flows() {
    return flows;
  }

  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  @Override
  @CheckForNull
  public TextRange getTextRange() {
    return textRange;
  }

  public Map<SoftwareQuality, ImpactSeverity> getOverriddenImpacts() {
    return overriddenImpacts;
  }

  public Optional<String> getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder();
    sb.append("[");
    sb.append("rule=").append(getRuleKey());
    if (textRange != null) {
      var startLine = textRange.getStartLine();
      sb.append(", line=").append(startLine);
    }
    if (clientInputFile != null) {
      sb.append(", file=").append(clientInputFile.uri());
    }
    sb.append("]");
    return sb.toString();
  }
}
