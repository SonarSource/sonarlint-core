/*
 * SonarLint Daemon
 * Copyright (C) 2009-2019 SonarSource SA
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

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.daemon.Daemon;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ProxyLogOutputTest {
  private StreamObserver<LogEvent> observer;

  @Before
  public void setup() {
    observer = mock(StreamObserver.class);
  }

  @Test
  public void testProxyLog() {
    ProxyLogOutput log = new ProxyLogOutput(mock(Daemon.class));
    log.log("log msg", Level.INFO);

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
  public void testLogError() {
    ProxyLogOutput log = new ProxyLogOutput(mock(Daemon.class));
    log.log("log msg", Level.INFO);

    log.setObserver(observer);
    log.log("msg", Level.ERROR);

    ArgumentCaptor<LogEvent> argument = ArgumentCaptor.forClass(LogEvent.class);
    verify(observer, atLeastOnce()).onNext(argument.capture());

    assertThat(argument.getAllValues()).extracting("isDebug", "level", "log")
      .contains(tuple(false, "ERROR", "msg"));
  }

  @Test
  public void testSetLogTwice() {
    ProxyLogOutput log = new ProxyLogOutput(mock(Daemon.class));
    log.setObserver(observer);
    log.setObserver(observer);
    verify(observer).onCompleted();
  }

  @Test
  public void testProxyLogError() {
    Daemon daemon = mock(Daemon.class);
    ProxyLogOutput log = new ProxyLogOutput(daemon);
    doThrow(StatusRuntimeException.class).when(observer).onNext(any(LogEvent.class));
    log.setObserver(observer);
    log.log("msg", Level.DEBUG);
    verify(daemon).stop();
  }
}
