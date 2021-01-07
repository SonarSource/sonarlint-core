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
package org.sonarsource.sonarlint.core.client.api.common;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.IncompatiblePluginApi;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.IncompatiblePluginVersion;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.LanguagesNotEnabled;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedDependency;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class SkipReasonTests {

  @Test
  void testLanguageNotEnabled() {
    SkipReason.LanguagesNotEnabled underTest = new LanguagesNotEnabled(asList(Language.JAVA));
    // Getters
    assertThat(underTest.getNotEnabledLanguages()).containsExactly(Language.JAVA);
    // Equals
    assertThat(underTest).isEqualTo(underTest);
    assertThat(underTest).isNotEqualTo(IncompatiblePluginApi.INSTANCE);
    assertThat(underTest).isNotEqualTo(new LanguagesNotEnabled(asList(Language.JS)));
    assertThat(underTest).isEqualTo(new LanguagesNotEnabled(asList(Language.JAVA)));
    // HashCode
    assertThat(underTest).hasSameHashCodeAs(underTest);
    assertThat(underTest).hasSameHashCodeAs(new LanguagesNotEnabled(asList(Language.JAVA)));
    // To String
    assertThat(underTest.toString()).isEqualTo("LanguagesNotEnabled [languages=[Java]]");
  }

  @Test
  void testUnsatisfiedDependency() {
    SkipReason.UnsatisfiedDependency underTest = new UnsatisfiedDependency("foo");
    // Getters
    assertThat(underTest.getDependencyKey()).isEqualTo("foo");
    // Equals
    assertThat(underTest).isEqualTo(underTest);
    assertThat(underTest).isNotEqualTo(IncompatiblePluginApi.INSTANCE);
    assertThat(underTest).isNotEqualTo(new UnsatisfiedDependency("bar"));
    assertThat(underTest).isEqualTo(new UnsatisfiedDependency("foo"));
    // HashCode
    assertThat(underTest).hasSameHashCodeAs(underTest);
    assertThat(underTest).hasSameHashCodeAs(new UnsatisfiedDependency("foo"));
    // To String
    assertThat(underTest.toString()).isEqualTo("UnsatisfiedDependency [dependencyKey=foo]");
  }

  @Test
  void testIncompatiblePluginVersion() {
    SkipReason.IncompatiblePluginVersion underTest = new IncompatiblePluginVersion("1.0");
    // Getters
    assertThat(underTest.getMinVersion()).isEqualTo("1.0");
    // Equals
    assertThat(underTest).isEqualTo(underTest);
    assertThat(underTest).isNotEqualTo(IncompatiblePluginApi.INSTANCE);
    assertThat(underTest).isNotEqualTo(new IncompatiblePluginVersion("2.0"));
    assertThat(underTest).isEqualTo(new IncompatiblePluginVersion("1.0"));
    // HashCode
    assertThat(underTest).hasSameHashCodeAs(underTest);
    assertThat(underTest).hasSameHashCodeAs(new IncompatiblePluginVersion("1.0"));
    // To String
    assertThat(underTest.toString()).isEqualTo("IncompatiblePluginVersion [minVersion=1.0]");
  }

  @Test
  void testUnsatisfiedRuntimeRequirementEqualsHashCode() {
    SkipReason.UnsatisfiedRuntimeRequirement underTest = new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0");
    // Getters
    assertThat(underTest.getMinVersion()).isEqualTo("2.0");
    assertThat(underTest.getCurrentVersion()).isEqualTo("1.0");
    // Equals
    assertThat(underTest)
      .isEqualTo(underTest)
      .isNotEqualTo(IncompatiblePluginApi.INSTANCE)
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, "1.0", "2.0"))
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "1.0"))
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "2.0", "1.0"))
      .isEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0"))
      // HashCode
      .hasSameHashCodeAs(underTest)
      .hasSameHashCodeAs(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0"));
    // To String
    assertThat(underTest.toString()).isEqualTo("UnsatisfiedRuntimeRequirement [runtime=JRE, currentVersion=1.0, minVersion=2.0]");
  }

}
