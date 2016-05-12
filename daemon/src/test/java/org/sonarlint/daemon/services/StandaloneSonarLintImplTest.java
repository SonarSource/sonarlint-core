/*
 * SonarLint Daemon
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarlint.daemon.services;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.daemon.services.AbstractSonarLint.DefaultClientInputFile;
import org.sonarlint.daemon.services.AbstractSonarLint.ProxyIssueListener;
import org.sonarlint.daemon.services.AbstractSonarLint.ProxyLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.StandaloneConfiguration;

import java.nio.charset.Charset;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StandaloneSonarLintImplTest {

  @Test
  public void testInputFile() {
    Path path = mock(Path.class);
    boolean isTest = true;
    Charset charset = mock(Charset.class);
    String userObject = new String();
    DefaultClientInputFile file = new DefaultClientInputFile(path, isTest, charset, userObject);

    assertThat(file.getCharset()).isEqualTo(charset);
    assertThat(file.isTest()).isEqualTo(isTest);
    assertThat(file.getPath()).isEqualTo(path);
    assertThat(file.getClientObject()).isEqualTo(userObject);
  }

  @Test
  public void testIssueListener() {
    StreamObserver<Issue> observer = mock(StreamObserver.class);
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
    assertThat(captured.getSeverity()).isEqualTo(Severity.MAJOR);
  }

  @Test
  public void testProxyLog() {
    ProxyLogOutput log = new ProxyLogOutput();
    log.log("log msg", Level.INFO);

    StreamObserver<LogEvent> observer = mock(StreamObserver.class);

    log.setObserver(observer);
    log.log("msg", Level.DEBUG);

    ArgumentCaptor<LogEvent> argument = ArgumentCaptor.forClass(LogEvent.class);
    verify(observer).onNext(argument.capture());
    LogEvent event = argument.getValue();

    assertThat(event.getIsDebug()).isTrue();
    assertThat(event.getLevel()).isEqualTo("DEBUG");
    assertThat(event.getLog()).isEqualTo("msg");
  }
  
  @Test
  public void testProxyLogError() {
    ProxyLogOutput log = new ProxyLogOutput();
    StreamObserver<LogEvent> observer = mock(StreamObserver.class);
    doThrow(StatusRuntimeException.class).when(observer).onNext(any(LogEvent.class));
    log.setObserver(observer);
    log.log("msg", Level.DEBUG);
  }
  
  @Test
  public void testStart() {
    StandaloneSonarLintImpl sonarlint = new StandaloneSonarLintImpl();
    StandaloneConfiguration config = StandaloneConfiguration.newBuilder().build();
    StreamObserver observer = mock(StreamObserver.class);
    sonarlint.start(config, observer);
    
    verify(observer).onCompleted();
    
  }
}
