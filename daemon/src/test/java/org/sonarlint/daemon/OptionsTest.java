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
package org.sonarlint.daemon;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OptionsTest {
  private Appender<ILoggingEvent> mockAppender;
  
  @Before
  public void addLogger() {
    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    mockAppender = mock(Appender.class);
    when(mockAppender.getName()).thenReturn("MOCK");
    root.addAppender(mockAppender);
  }
  
  @After
  public void removeLogger() {
    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    root.detachAppender(mockAppender);
  }
  
  @Test
  public void testPort() throws ParseException {
    String[] args = {"--port", "1234"};
    assertThat(Options.parse(args).getPort()).isEqualTo("1234");
  }

  @Test
  public void testHelp() throws ParseException {
    String[] args = {"-h"};
    assertThat(Options.parse(args).isHelp()).isTrue();
  }

  @Test(expected = ParseException.class)
  public void testInvalid() throws ParseException {
    String[] args = {"--unknown", "1234"};
    Options.parse(args);
  }
  
  @Test
  public void testUsage() {
    Options.printUsage();
    
    verify(mockAppender).doAppend(argThat(new ArgumentMatcher<ILoggingEvent>() {
      @Override
      public boolean matches(final Object argument) {
        return ((LoggingEvent)argument).getFormattedMessage().contains("usage: sonarlint-daemon");
      }
    }));
  }
}
