/*
 * SonarLint Daemon
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
package org.sonarlint.daemon;

import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OptionsTest {
  private Logger logger;

  @Before
  public void addLogger() {
    logger = mock(Logger.class);
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
    Options.printUsage(logger);

    verify(logger).info("usage: sonarlint-daemon [options]");
  }
}
