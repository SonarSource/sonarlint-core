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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.IllegalSelectorException;
import java.nio.file.Paths;
import java.util.Collections;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.InitializeParams;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarlint.languageserver.SonarLintLanguageServer.getStoragePath;

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

  @Test
  public void makeQualityGateHappy() throws Exception {
    SonarLintLanguageServer server = new SonarLintLanguageServer(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), Collections.emptyList());
    server.error("Foo", new IllegalSelectorException());
    server.warn("Foo");
    assertThat(server.getTextDocumentService().codeLens(null)).isNull();
    assertThat(server.getTextDocumentService().completion(null)).isNull();
    assertThat(server.getTextDocumentService().definition(null)).isNull();
    assertThat(server.getTextDocumentService().documentHighlight(null)).isNull();
    assertThat(server.getTextDocumentService().documentSymbol(null)).isNull();
    assertThat(server.getTextDocumentService().formatting(null)).isNull();
    assertThat(server.getTextDocumentService().hover(null)).isNull();
    assertThat(server.getTextDocumentService().onTypeFormatting(null)).isNull();
    assertThat(server.getTextDocumentService().rangeFormatting(null)).isNull();
    assertThat(server.getTextDocumentService().references(null)).isNull();
    assertThat(server.getTextDocumentService().rename(null)).isNull();
    assertThat(server.getTextDocumentService().resolveCodeLens(null)).isNull();
    assertThat(server.getTextDocumentService().resolveCompletionItem(null)).isNull();
    assertThat(server.getTextDocumentService().signatureHelp(null)).isNull();

    server.getWorkspaceService().didChangeWatchedFiles(null);
    assertThat(server.getWorkspaceService().executeCommand(null)).isNull();
    assertThat(server.getWorkspaceService().symbol(null)).isNull();
  }

  @Test
  public void getStoragePath_should_return_null_when_configuration_missing() {
    assertThat(getStoragePath(null, null)).isNull();
  }

  @Test
  public void getStoragePath_should_return_old_path_when_product_key_missing() {
    String oldStorage = "dummy";
    assertThat(getStoragePath(null, oldStorage)).isEqualTo(Paths.get(oldStorage));
  }

  @Test
  public void getStoragePath_should_return_new_path_when_product_key_present() {
    String productKey = "vim";
    assertThat(getStoragePath(productKey, "dummy")).isEqualTo(TelemetryPathManager.getPath(productKey));
  }

  @Test
  public void initialize_should_not_crash_when_disableTelemetry_param_missing() {
    SonarLintLanguageServer ls = new SonarLintLanguageServer(mock(InputStream.class), mock(OutputStream.class), Collections.emptyList());
    InitializeParams params = mock(InitializeParams.class);
    when(params.getInitializationOptions()).thenReturn(Collections.emptyMap());
    ls.initialize(params);
  }
}
