/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getCurrentLts;
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getMinimalSupportedVersion;

class VersionUtilsTests {

  @Test
  void grace_period_should_be_false_if_connected_current_lts() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(getCurrentLts())).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(getCurrentLts().getName() + ".1"))).isFalse();
  }

  @Test
  void grace_period_should_be_false_if_connected_outdated_version() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("5.9"))).isFalse();
  }

  @Test
  void grace_period_should_be_true_if_connected_during_grace_period() {
    // read isVersionSupportedDuringGracePeriod javadoc
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(getMinimalSupportedVersion())).isTrue();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(getMinimalSupportedVersion().getName() + ".1"))).isTrue();
  }
  
  @Test
  void test_version_in_between_minimal_and_current_LTS() {
    // read isVersionSupportedDuringGracePeriod javadoc
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("10.4.1"))).isTrue();
  }
}
