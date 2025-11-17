/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.active.rules.ActiveRuleDetails;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.tracking.TextRangeUtils;

public class RawIssue {

  private final Issue issue;
  private final ActiveRuleDetails activeRule;
  private final Map<SoftwareQuality, ImpactSeverity> impacts = new EnumMap<>(SoftwareQuality.class);
  @Nullable
  private final String textRangeHash;
  @Nullable
  private final String lineHash;

  public RawIssue(Issue issue) {
    this.issue = issue;
    this.activeRule = (ActiveRuleDetails) issue.getActiveRule();
    this.impacts.putAll(activeRule.impacts());
    this.impacts.putAll(issue.getOverriddenImpacts());
    var textRangeWithHash = TextRangeUtils.getTextRangeWithHash(getTextRange(), getClientInputFile());
    this.textRangeHash = textRangeWithHash == null ? null : textRangeWithHash.getHash();
    var lineWithHash = TextRangeUtils.getLineWithHash(issue.getTextRange(), getClientInputFile());
    this.lineHash = lineWithHash == null ? null : lineWithHash.getHash();
  }

  public IssueSeverity getSeverity() {
    return activeRule.issueSeverity();
  }

  public RuleType getRuleType() {
    return activeRule.type();
  }

  public boolean isSecurityHotspot() {
    return getRuleType() == RuleType.SECURITY_HOTSPOT;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return activeRule.cleanCodeAttribute();
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public String getRuleKey() {
    return activeRule.ruleKeyString();
  }

  public String getMessage() {
    return issue.getMessage();
  }

  public List<Flow> getFlows() {
    return issue.flows();
  }

  public List<QuickFix> getQuickFixes() {
    return issue.quickFixes();
  }

  @CheckForNull
  public TextRange getTextRange() {
    return issue.getTextRange();
  }

  @CheckForNull
  public Path getIdeRelativePath() {
    var inputFile = issue.getInputFile();
    return inputFile != null ? Path.of(inputFile.relativePath()) : null;
  }

  @CheckForNull
  public URI getFileUri() {
    var inputFile = issue.getInputFile();
    return inputFile != null ? inputFile.uri() : null;
  }

  public boolean isInFile() {
    return issue.getInputFile() != null;
  }

  @CheckForNull
  public ClientInputFile getClientInputFile() {
    return issue.getInputFile();
  }

  @CheckForNull
  public VulnerabilityProbability getVulnerabilityProbability() {
    return activeRule.vulnerabilityProbability();
  }

  @CheckForNull
  public String getRuleDescriptionContextKey() {
    return issue.getRuleDescriptionContextKey().orElse(null);
  }

  public Collection<Integer> getLineNumbers() {
    Set<Integer> lineNumbers = new HashSet<>();
    Optional.ofNullable(getTextRange())
      .map(textRange -> IntStream.rangeClosed(textRange.getStartLine(), textRange.getEndLine()))
      .ifPresent(intStream -> intStream.forEach(lineNumbers::add));

    getFlows()
      .forEach(flow -> flow.locations().stream()
        .filter(issueLocation -> Objects.nonNull(issueLocation.getStartLine()))
        .filter(issueLocation -> Objects.nonNull(issueLocation.getEndLine()))
        .map(issueLocation -> IntStream.rangeClosed(issueLocation.getStartLine(), issueLocation.getEndLine()))
        .forEach(intStream -> intStream.forEach(lineNumbers::add)));

    return lineNumbers;
  }

  public Optional<Integer> getLine() {
    return Optional.ofNullable(issue.getStartLine());
  }

  public Optional<String> getTextRangeHash() {
    return Optional.ofNullable(textRangeHash);
  }

  public Optional<String> getLineHash() {
    return Optional.ofNullable(lineHash);
  }
}
