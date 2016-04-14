/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.util;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilsTest {
  @Test
  public void testJarVersions() {
    assertThat(VersionUtils.getJarVersion("sonar-governance-plugin-1.0-build251.jar")).isEqualTo("1.0");
    assertThat(VersionUtils.getJarVersion("qualinsight-sonarqube-badges-1.2.1.jar")).isEqualTo("1.2.1");
    assertThat(VersionUtils.getJarVersion("sonar-java-plugin-3.13-build4943.jar")).isEqualTo("3.13");
    assertThat(VersionUtils.getJarVersion("sonar-github-plugin-1.2-M3_2016-04-06.jar")).isEqualTo("1.2");
  }

  @Test
  public void testVersionFallback() {
    String version = VersionUtils.getLibraryVersionFallback();
    assertThat(isVersion(version)).isTrue();
  }

  @Test
  public void testVersion() {
    String version = VersionUtils.getLibraryVersion();
    assertThat(isVersion(version)).isTrue();
  }

  @Test
  public void testVersionAssert() {
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
    String regex = "(\\d+\\.\\d+(?:\\.\\d+)*).*";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(version);

    return matcher.find();
  }
}
