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
package org.sonarsource.sonarlint.core.container.connected.validate;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginVersionCheckerTests {
  private PluginVersionChecker underTest;

  @BeforeEach
  public void setUp() {
    underTest = new PluginVersionChecker();
  }

  @Test
  void test() throws IOException {
    assertThat(underTest.getMinimumVersion("java")).isEqualTo("5.1.0.13090");
    assertThat(underTest.getMinimumVersion("unknown")).isNull();
  }

  @Test
  void isVersionSupported() {
    assertThat(underTest.isVersionSupported("java", "5.1.0.13090")).isTrue();
    assertThat(underTest.isVersionSupported("java", "3.9.0.13090")).isFalse();
    assertThat(underTest.isVersionSupported("unknown", "4.0")).isTrue();
  }
}
