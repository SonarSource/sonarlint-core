/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTests {

  @Test
  void test_fields_of_snapshot_versions() {
    var version = Version.create("1.2.3-SNAPSHOT");
    assertThat(version.getMajor()).isEqualTo(1);
    assertThat(version.getMinor()).isEqualTo(2);
    assertThat(version.getPatch()).isEqualTo(3);
    assertThat(version.getBuild()).isEqualTo(0);
    assertThat(version.getQualifier()).isEqualTo("SNAPSHOT");
  }

  @Test
  void test_fields_of_releases() {
    var version = Version.create("1.2");
    assertThat(version.getMajor()).isEqualTo(1);
    assertThat(version.getMinor()).isEqualTo(2);
    assertThat(version.getPatch()).isEqualTo(0);
    assertThat(version.getBuild()).isEqualTo(0);
    assertThat(version.getQualifier()).isEmpty();
  }

  @Test
  void compare_releases() {
    var version12 = Version.create("1.2");
    var version121 = Version.create("1.2.1");

    assertThat(version12)
      .hasToString("1.2")
      .isEqualByComparingTo(version12);
    assertThat(version121)
      .isEqualByComparingTo(version121)
      .isGreaterThan(version12);
  }

  @Test
  void compare_snapshots() {
    var version12 = Version.create("1.2");
    var version12Snapshot = Version.create("1.2-SNAPSHOT");
    var version121Snapshot = Version.create("1.2.1-SNAPSHOT");
    var version12RC = Version.create("1.2-RC1");

    assertThat(version12).isGreaterThan(version12Snapshot);
    assertThat(version12Snapshot).isEqualByComparingTo(version12Snapshot);
    assertThat(version121Snapshot).isGreaterThan(version12Snapshot);
    assertThat(version12Snapshot).isGreaterThan(version12RC);
  }

  @Test
  void compare_release_candidates() {
    var version12 = Version.create("1.2");
    var version12Snapshot = Version.create("1.2-SNAPSHOT");
    var version12RC1 = Version.create("1.2-RC1");
    var version12RC2 = Version.create("1.2-RC2");

    assertThat(version12RC1)
      .isLessThan(version12Snapshot)
      .isEqualByComparingTo(version12RC1)
      .isLessThan(version12RC2)
      .isLessThan(version12);
  }

  @Test
  void testTrim() {
    var version12 = Version.create("   1.2  ");

    assertThat(version12.getName()).isEqualTo("1.2");
    assertThat(version12).isEqualTo(Version.create("1.2"));
  }

  @Test
  void testDefaultNumberIsZero() {
    var version12 = Version.create("1.2");
    var version120 = Version.create("1.2.0");

    assertThat(version12).isEqualTo(version120);
    assertThat(version120).isEqualTo(version12);
  }

  @Test
  void testCompareOnTwoDigits() {
    var version1dot10 = Version.create("1.10");
    var version1dot1 = Version.create("1.1");
    var version1dot9 = Version.create("1.9");

    assertThat(version1dot10.compareTo(version1dot1) > 0).isTrue();
    assertThat(version1dot10.compareTo(version1dot9) > 0).isTrue();
  }

  @Test
  void testFields() {
    var version = Version.create("1.10.2");

    assertThat(version.getName()).isEqualTo("1.10.2");
    assertThat(version).hasToString("1.10.2");
    assertThat(version.getMajor()).isEqualTo(1);
    assertThat(version.getMinor()).isEqualTo(10);
    assertThat(version.getPatch()).isEqualTo(2);
    assertThat(version.getBuild()).isEqualTo(0);
  }

  @Test
  void testPatchFieldsEquals() {
    var version = Version.create("1.2.3.4");

    assertThat(version.getPatch()).isEqualTo(3);
    assertThat(version.getBuild()).isEqualTo(4);

    assertThat(version)
      .isEqualTo(version)
      .isEqualTo(Version.create("1.2.3.4"))
      .isNotEqualTo(Version.create("1.2.3.5"));
  }

  @Test
  void removeQualifier() {
    var version = Version.create("1.2.3-SNAPSHOT").removeQualifier();

    assertThat(version.getMajor()).isEqualTo(1);
    assertThat(version.getMinor()).isEqualTo(2);
    assertThat(version.getPatch()).isEqualTo(3);
    assertThat(version.getQualifier()).isEmpty();
  }
}
