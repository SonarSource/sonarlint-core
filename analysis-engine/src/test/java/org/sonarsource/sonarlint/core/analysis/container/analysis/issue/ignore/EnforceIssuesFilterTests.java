/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata.Metadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputProject;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssueInclusionPatternInitializer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.ignore.pattern.IssuePattern;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultFilterableIssue;
import testutils.OnDiskTestClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnforceIssuesFilterTests {

  private IssueInclusionPatternInitializer exclusionPatternInitializer;
  private EnforceIssuesFilter ignoreFilter;
  private DefaultFilterableIssue issue;
  private IssueFilterChain chain;

  @BeforeEach
  void init() {
    exclusionPatternInitializer = mock(IssueInclusionPatternInitializer.class);
    issue = mock(DefaultFilterableIssue.class);
    chain = mock(IssueFilterChain.class);
    when(chain.accept(issue)).thenReturn(true);
  }

  @Test
  void shouldPassToChainIfNoConfiguredPatterns() {
    ignoreFilter = new EnforceIssuesFilter(exclusionPatternInitializer);
    assertThat(ignoreFilter.accept(issue, chain)).isTrue();
    verify(chain).accept(issue);
  }

  @Test
  void shouldPassToChainIfRuleDoesNotMatch() {
    var rule = "rule";
    var ruleKey = mock(RuleKey.class);
    when(ruleKey.toString()).thenReturn(rule);
    when(issue.ruleKey()).thenReturn(ruleKey);

    var matching = mock(IssuePattern.class);
    when(matching.matchRule(ruleKey)).thenReturn(false);
    when(exclusionPatternInitializer.getMulticriteriaPatterns()).thenReturn(List.of(matching));

    ignoreFilter = new EnforceIssuesFilter(exclusionPatternInitializer);
    assertThat(ignoreFilter.accept(issue, chain)).isTrue();
    verify(chain).accept(issue);
  }

  @Test
  void shouldAcceptIssueIfFullyMatched() {
    var rule = "rule";
    var path = "org/sonar/api/Issue.java";
    var ruleKey = mock(RuleKey.class);
    when(ruleKey.toString()).thenReturn(rule);
    when(issue.ruleKey()).thenReturn(ruleKey);

    var matching = mock(IssuePattern.class);
    when(matching.matchRule(ruleKey)).thenReturn(true);
    when(matching.matchFile(path)).thenReturn(true);
    when(exclusionPatternInitializer.getMulticriteriaPatterns()).thenReturn(List.of(matching));
    when(issue.getComponent()).thenReturn(createComponentWithPath(path));

    ignoreFilter = new EnforceIssuesFilter(exclusionPatternInitializer);
    assertThat(ignoreFilter.accept(issue, chain)).isTrue();
    verifyNoInteractions(chain);
  }

  private InputComponent createComponentWithPath(String path) {
    return new SonarLintInputFile(new OnDiskTestClientInputFile(Paths.get(path), path, false, StandardCharsets.UTF_8),
      f -> mock(Metadata.class));
  }

  @Test
  void shouldRefuseIssueIfRuleMatchesButNotPath() {
    var rule = "rule";
    var path = "org/sonar/api/Issue.java";
    var componentKey = "org.sonar.api.Issue";
    var ruleKey = mock(RuleKey.class);
    when(ruleKey.toString()).thenReturn(rule);
    when(issue.ruleKey()).thenReturn(ruleKey);
    when(issue.componentKey()).thenReturn(componentKey);

    var matching = mock(IssuePattern.class);
    when(matching.matchRule(ruleKey)).thenReturn(true);
    when(matching.matchFile(path)).thenReturn(false);
    when(exclusionPatternInitializer.getMulticriteriaPatterns()).thenReturn(List.of(matching));
    when(issue.getComponent()).thenReturn(createComponentWithPath(path));

    ignoreFilter = new EnforceIssuesFilter(exclusionPatternInitializer);
    assertThat(ignoreFilter.accept(issue, chain)).isFalse();
    verifyNoInteractions(chain);
  }

  @Test
  void shouldRefuseIssueIfRuleMatchesAndNotFile() throws IOException {
    var rule = "rule";
    var path = "org/sonar/api/Issue.java";
    var componentKey = "org.sonar.api.Issue";
    var ruleKey = mock(RuleKey.class);
    when(ruleKey.toString()).thenReturn(rule);
    when(issue.ruleKey()).thenReturn(ruleKey);

    var matching = mock(IssuePattern.class);
    when(matching.matchRule(ruleKey)).thenReturn(true);
    when(matching.matchFile(path)).thenReturn(true);
    when(exclusionPatternInitializer.getMulticriteriaPatterns()).thenReturn(List.of(matching));
    when(issue.getComponent()).thenReturn(new SonarLintInputProject());

    ignoreFilter = new EnforceIssuesFilter(exclusionPatternInitializer);
    assertThat(ignoreFilter.accept(issue, chain)).isFalse();
    verifyNoInteractions(chain);
  }
}
