/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.issue.ignore;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.WildcardPattern;
import org.sonarsource.sonarlint.core.analyzer.issue.DefaultFilterableIssue;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IgnoreIssuesFilterTest {

  private DefaultFilterableIssue issue = mock(DefaultFilterableIssue.class);
  private IssueFilterChain chain = mock(IssueFilterChain.class);
  private IgnoreIssuesFilter underTest = new IgnoreIssuesFilter();
  private SonarLintInputFile component;
  private RuleKey ruleKey = RuleKey.of("foo", "bar");

  @Before
  public void prepare() {
    component = mock(SonarLintInputFile.class);
    when(issue.getComponent()).thenReturn(component);
    when(issue.ruleKey()).thenReturn(ruleKey);
  }

  @Test
  public void shouldPassToChainIfMatcherHasNoPatternForIssue() {
    when(chain.accept(issue)).thenReturn(true);
    assertThat(underTest.accept(issue, chain)).isTrue();
    verify(chain).accept(any());
  }

  @Test
  public void shouldRejectIfRulePatternMatches() {
    WildcardPattern pattern = mock(WildcardPattern.class);
    when(pattern.match(ruleKey.toString())).thenReturn(true);
    underTest.addRuleExclusionPatternForComponent(component, pattern);

    assertThat(underTest.accept(issue, chain)).isFalse();
  }

  @Test
  public void shouldAcceptIfRulePatternDoesNotMatch() {
    WildcardPattern pattern = mock(WildcardPattern.class);
    when(pattern.match(ruleKey.toString())).thenReturn(false);
    underTest.addRuleExclusionPatternForComponent(component, pattern);

    assertThat(underTest.accept(issue, chain)).isFalse();
  }
}
