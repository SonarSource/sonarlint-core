/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.commons;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginsMinVersionsTests {
  private PluginsMinVersions underTest;

  @BeforeEach
  void setUp() {
    underTest = new PluginsMinVersions();
  }

  @Test
  void getMinimumVersion() {
    assertThat(underTest.getMinimumVersion("java")).isEqualTo("7.16.0.30901");
    assertThat(underTest.getMinimumVersion("unknown")).isNull();
  }

  @Test
  void isVersionSupported() {
    assertThat(underTest.isVersionSupported("java", "7.16.0.30901")).isTrue();
    assertThat(underTest.isVersionSupported("java", "7.16.0.30901-SNAPSHOT")).isTrue();
    assertThat(underTest.isVersionSupported("java", "7.16.0.20901")).isFalse();
    assertThat(underTest.isVersionSupported("unknown", "4.0")).isTrue();
  }
}
