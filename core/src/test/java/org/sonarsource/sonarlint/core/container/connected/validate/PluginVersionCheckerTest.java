/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PluginVersionCheckerTest {
  private PluginVersionChecker checker;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    checker = new PluginVersionChecker();
  }

  @Test
  public void test() throws IOException {
    assertThat(checker.getMinimumVersion("java")).isEqualTo("4.0");
    assertThat(checker.getMinimumVersion("unknown")).isNull();
  }

  @Test
  public void testStreamSupport() {
    assertThat(checker.getMinimumStreamSupportVersion("java")).isEqualTo("4.7");
    assertThat(checker.getMinimumStreamSupportVersion("unknown")).isNull();
  }
}
