/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.languageserver;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarLintLanguageServerTest {

  @Test
  public void testNotConvertGlobalIssues() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(null);
    assertThat(SonarLintLanguageServer.convert(issue)).isEmpty();
  }

  @Test
  public void testNotConvertSeverity() {
    Issue issue = mock(Issue.class);
    when(issue.getStartLine()).thenReturn(1);
    when(issue.getSeverity()).thenReturn("BLOCKER");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("CRITICAL");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    when(issue.getSeverity()).thenReturn("MAJOR");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    when(issue.getSeverity()).thenReturn("MINOR");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Information);
    when(issue.getSeverity()).thenReturn("INFO");
    assertThat(SonarLintLanguageServer.convert(issue).get().getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
  }

}
