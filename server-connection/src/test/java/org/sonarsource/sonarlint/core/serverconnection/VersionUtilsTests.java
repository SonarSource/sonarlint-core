/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getCurrentLts;
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getPreviousLts;

class VersionUtilsTests {

  @Test
  void testJarVersions() {
    assertThat(VersionUtils.getJarVersion("sonar-governance-plugin-1.0-build251.jar")).isEqualTo("1.0");
    assertThat(VersionUtils.getJarVersion("qualinsight-sonarqube-badges-1.2.1.jar")).isEqualTo("1.2.1");
    assertThat(VersionUtils.getJarVersion("sonar-java-plugin-3.13-build4943.jar")).isEqualTo("3.13");
    assertThat(VersionUtils.getJarVersion("sonar-github-plugin-1.2-M3_2016-04-06.jar")).isEqualTo("1.2");
  }

  @Test
  void grace_period_should_be_false_if_connected_current_lts() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(getCurrentLts())).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(getCurrentLts().getName() + ".1"))).isFalse();
  }

  @Test
  void grace_period_should_be_false_if_connected_outdated_version() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("7.9"))).isFalse();
  }

  @Test
  void grace_period_should_be_true_if_connected_during_grace_period() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(getPreviousLts())).isTrue();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(getPreviousLts().getName() + ".1"))).isTrue();
  }

}
