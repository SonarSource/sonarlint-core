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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.WildcardPattern;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultFilterableIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

 class IgnoreIssuesFilterTests {

  private final DefaultFilterableIssue issue = mock(DefaultFilterableIssue.class);
  private final IssueFilterChain chain = mock(IssueFilterChain.class);
  private final IgnoreIssuesFilter underTest = new IgnoreIssuesFilter();
  private SonarLintInputFile component;
  private final RuleKey ruleKey = RuleKey.of("foo", "bar");

  @BeforeEach
  void prepare() {
    component = mock(SonarLintInputFile.class);
    when(issue.getComponent()).thenReturn(component);
    when(issue.ruleKey()).thenReturn(ruleKey);
  }

  @Test
  void shouldPassToChainIfMatcherHasNoPatternForIssue() {
    when(chain.accept(issue)).thenReturn(true);
    assertThat(underTest.accept(issue, chain)).isTrue();
    verify(chain).accept(any());
  }

  @Test
  void shouldRejectIfRulePatternMatches() {
    var pattern = mock(WildcardPattern.class);
    when(pattern.match(ruleKey.toString())).thenReturn(true);
    underTest.addRuleExclusionPatternForComponent(component, pattern);

    assertThat(underTest.accept(issue, chain)).isFalse();
  }

  @Test
  void shouldAcceptIfRulePatternDoesNotMatch() {
    var pattern = mock(WildcardPattern.class);
    when(pattern.match(ruleKey.toString())).thenReturn(false);
    underTest.addRuleExclusionPatternForComponent(component, pattern);

    assertThat(underTest.accept(issue, chain)).isFalse();
  }
}
