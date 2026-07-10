/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getCurrentCommunityBuildLts;
import static org.sonarsource.sonarlint.core.serverconnection.VersionUtils.getCurrentLts;

class VersionUtilsTests {

  @Test
  void current_lts_should_be_exposed_for_both_numbering_systems() {
    assertThat(getCurrentLts()).isEqualTo(Version.create("2025.1"));
    assertThat(getCurrentCommunityBuildLts()).isEqualTo(Version.create("25.1"));
  }

  @Test
  void grace_period_should_be_false_if_connected_current_lts() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(getCurrentLts())).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("25.1"))).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(getCurrentLts().getName() + ".1"))).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("25.1.1"))).isFalse();
  }

  @Test
  void grace_period_should_be_false_if_connected_outdated_version() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("5.9"))).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("9.8"))).isFalse();
  }

  @Test
  void grace_period_should_be_true_if_connected_during_grace_period() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(VersionUtils.MINIMAL_SUPPORTED_VERSION_SHORT)).isTrue();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create(VersionUtils.MINIMAL_SUPPORTED_VERSION_SHORT.getName() + ".1"))).isTrue();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("24.12"))).isTrue();
  }

  @Test
  void grace_period_should_be_false_if_connected_newer_than_current_lts() {
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("2025.2"))).isFalse();
    assertThat(VersionUtils.isVersionSupportedDuringGracePeriod(Version.create("25.2"))).isFalse();
  }

}
