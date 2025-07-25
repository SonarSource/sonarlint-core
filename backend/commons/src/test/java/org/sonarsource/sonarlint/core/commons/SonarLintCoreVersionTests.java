/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintCoreVersionTests {

  @Test
  void testVersionFallback() {
    var version = SonarLintCoreVersion.getLibraryVersion();
    assertThat(isVersion(version)).isTrue();
  }

  @Test
  void testVersion() {
    var version = SonarLintCoreVersion.get();
    assertThat(isVersion(version)).isTrue();
  }

  @Test
  void testVersionAssert() {
    assertThat(isVersion("2.1")).isTrue();
    assertThat(isVersion("2.0-SNAPSHOT")).isTrue();
    assertThat(isVersion("2.0.0-SNAPSHOT")).isTrue();
    assertThat(isVersion("2-SNAPSHOT")).isFalse();
    assertThat(isVersion("unknown")).isFalse();
    assertThat(isVersion(null)).isFalse();
  }

  private boolean isVersion(String version) {
    if (version == null) {
      return false;
    }
    var regex = "(\\d+\\.\\d+(?:\\.\\d+)*).*";
    var pattern = Pattern.compile(regex);
    var matcher = pattern.matcher(version);

    return matcher.find();
  }
}
