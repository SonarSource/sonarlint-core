/*
 * SonarLint Daemon
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.daemon.model;

import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProxyIssueListenerTest {
  @Test
  public void testIssueListener() {
    for (Severity s : Severity.values()) {
      if (s == Severity.UNRECOGNIZED) {
        continue;
      }
      testWithSeverity(s);
    }

    for (Type t : Type.values()) {
      if (t == Type.UNRECOGNIZED) {
        continue;
      }
      testWithType(t);
    }
  }

  private void testWithSeverity(Severity severity) {
    StreamObserver<Issue> observer = mock(StreamObserver.class);
    ClientInputFile inputFile = mock(ClientInputFile.class);
    when(inputFile.getPath()).thenReturn("filename");
    when(inputFile.getClientObject()).thenReturn("obj");
    ProxyIssueListener listener = new ProxyIssueListener(observer);

    org.sonarsource.sonarlint.core.client.api.common.analysis.Issue i = mock(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.class);
    when(i.getEndLine()).thenReturn(10);
    when(i.getStartLine()).thenReturn(11);
    when(i.getStartLineOffset()).thenReturn(12);
    when(i.getEndLineOffset()).thenReturn(13);
    when(i.getMessage()).thenReturn("msg");
    when(i.getRuleKey()).thenReturn("key");
    when(i.getRuleName()).thenReturn("name");
    when(i.getSeverity()).thenReturn(severity.toString().toUpperCase());
    when(i.getInputFile()).thenReturn(inputFile);

    listener.handle(i);

    ArgumentCaptor<Issue> argument = ArgumentCaptor.forClass(Issue.class);
    verify(observer).onNext(argument.capture());

    Issue captured = argument.getValue();
    assertThat(captured.getEndLine()).isEqualTo(10);
    assertThat(captured.getStartLine()).isEqualTo(11);
    assertThat(captured.getStartLineOffset()).isEqualTo(12);
    assertThat(captured.getEndLineOffset()).isEqualTo(13);
    assertThat(captured.getMessage()).isEqualTo("msg");
    assertThat(captured.getRuleKey()).isEqualTo("key");
    assertThat(captured.getRuleName()).isEqualTo("name");
    assertThat(captured.getSeverity()).isEqualTo(severity);
    assertThat(captured.getFilePath()).isEqualTo("filename");
    assertThat(captured.getUserObject()).isEqualTo("obj");
  }

  private void testWithType(Type type) {
    StreamObserver<Issue> observer = mock(StreamObserver.class);
    ClientInputFile inputFile = mock(ClientInputFile.class);
    when(inputFile.getPath()).thenReturn("filename");
    when(inputFile.getClientObject()).thenReturn("obj");
    ProxyIssueListener listener = new ProxyIssueListener(observer);

    org.sonarsource.sonarlint.core.client.api.common.analysis.Issue i = mock(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.class);
    when(i.getEndLine()).thenReturn(10);
    when(i.getStartLine()).thenReturn(11);
    when(i.getStartLineOffset()).thenReturn(12);
    when(i.getEndLineOffset()).thenReturn(13);
    when(i.getMessage()).thenReturn("msg");
    when(i.getRuleKey()).thenReturn("key");
    when(i.getRuleName()).thenReturn("name");
    when(i.getSeverity()).thenReturn("MAJOR");
    when(i.getType()).thenReturn(type.toString().toUpperCase());
    when(i.getInputFile()).thenReturn(inputFile);

    listener.handle(i);

    ArgumentCaptor<Issue> argument = ArgumentCaptor.forClass(Issue.class);
    verify(observer).onNext(argument.capture());

    Issue captured = argument.getValue();
    assertThat(captured.getEndLine()).isEqualTo(10);
    assertThat(captured.getStartLine()).isEqualTo(11);
    assertThat(captured.getStartLineOffset()).isEqualTo(12);
    assertThat(captured.getEndLineOffset()).isEqualTo(13);
    assertThat(captured.getMessage()).isEqualTo("msg");
    assertThat(captured.getRuleKey()).isEqualTo("key");
    assertThat(captured.getRuleName()).isEqualTo("name");
    assertThat(captured.getType()).isEqualTo(type);
    assertThat(captured.getFilePath()).isEqualTo("filename");
    assertThat(captured.getUserObject()).isEqualTo("obj");
  }

  @Test
  public void dont_fail_if_no_user_obj() {
    StreamObserver<Issue> observer = mock(StreamObserver.class);
    ClientInputFile inputFile = mock(ClientInputFile.class);
    when(inputFile.getPath()).thenReturn("filename");
    ProxyIssueListener listener = new ProxyIssueListener(observer);

    org.sonarsource.sonarlint.core.client.api.common.analysis.Issue i = mock(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.class);
    when(i.getEndLine()).thenReturn(10);
    when(i.getStartLine()).thenReturn(11);
    when(i.getStartLineOffset()).thenReturn(12);
    when(i.getEndLineOffset()).thenReturn(13);
    when(i.getMessage()).thenReturn("msg");
    when(i.getRuleKey()).thenReturn("key");
    when(i.getRuleName()).thenReturn("name");
    when(i.getSeverity()).thenReturn("MINOR");
    when(i.getInputFile()).thenReturn(inputFile);

    listener.handle(i);

    ArgumentCaptor<Issue> argument = ArgumentCaptor.forClass(Issue.class);
    verify(observer).onNext(argument.capture());

    Issue captured = argument.getValue();
    assertThat(captured.getEndLine()).isEqualTo(10);
    assertThat(captured.getStartLine()).isEqualTo(11);
    assertThat(captured.getStartLineOffset()).isEqualTo(12);
    assertThat(captured.getEndLineOffset()).isEqualTo(13);
    assertThat(captured.getMessage()).isEqualTo("msg");
    assertThat(captured.getRuleKey()).isEqualTo("key");
    assertThat(captured.getRuleName()).isEqualTo("name");
    assertThat(captured.getSeverity()).isEqualTo(Severity.MINOR);
    assertThat(captured.getFilePath()).isEqualTo("filename");
    assertThat(captured.getUserObject()).isEqualTo("");
  }
}
