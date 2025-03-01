/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason.IncompatiblePluginApi;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason.LanguagesNotEnabled;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason.UnsatisfiedDependency;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason.UnsatisfiedRuntimeRequirement;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement;

import static org.assertj.core.api.Assertions.assertThat;

class SkipReasonTests {

  @Test
  void testLanguageNotEnabled_getters_equals_hashcode_tostring() {
    var underTest = new LanguagesNotEnabled(List.of(SonarLanguage.JAVA));
    // Getters
    assertThat(underTest.getNotEnabledLanguages())
      .containsExactly(SonarLanguage.JAVA);
    assertThat(underTest)
      // Equals
      .isEqualTo(underTest)
      .isNotEqualTo(IncompatiblePluginApi.INSTANCE)
      .isNotEqualTo(new LanguagesNotEnabled(List.of(SonarLanguage.JS)))
      .isEqualTo(new LanguagesNotEnabled(List.of(SonarLanguage.JAVA)))
      // HashCode
      .hasSameHashCodeAs(underTest)
      .hasSameHashCodeAs(new LanguagesNotEnabled(List.of(SonarLanguage.JAVA)))
      // To String
      .hasToString("LanguagesNotEnabled [languages=[JAVA]]");
  }

  @Test
  void testUnsatisfiedDependency_getters_equals_hashcode_tostring() {
    var underTest = new UnsatisfiedDependency("foo");
    // Getters
    assertThat(underTest.getDependencyKey()).isEqualTo("foo");
    assertThat(underTest)
      // Equals
      .isEqualTo(underTest)
      .isNotEqualTo(IncompatiblePluginApi.INSTANCE)
      .isNotEqualTo(new UnsatisfiedDependency("bar"))
      .isEqualTo(new UnsatisfiedDependency("foo"))
      // HashCode
      .hasSameHashCodeAs(underTest)
      .hasSameHashCodeAs(new UnsatisfiedDependency("foo"))
      // To String
      .hasToString("UnsatisfiedDependency [dependencyKey=foo]");
  }

  @Test
  void testUnsatisfiedRuntimeRequirement_getters_equals_hashcode_tostring() {
    var underTest = new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0");
    // Getters
    assertThat(underTest.getMinVersion()).isEqualTo("2.0");
    assertThat(underTest.getCurrentVersion()).isEqualTo("1.0");
    assertThat(underTest)
      // Equals
      .isEqualTo(underTest)
      .isNotEqualTo(IncompatiblePluginApi.INSTANCE)
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.NODEJS, "1.0", "2.0"))
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "1.0"))
      .isNotEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "2.0", "1.0"))
      .isEqualTo(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0"))
      // HashCode
      .hasSameHashCodeAs(underTest)
      .hasSameHashCodeAs(new UnsatisfiedRuntimeRequirement(RuntimeRequirement.JRE, "1.0", "2.0"))
      // To String
      .hasToString("UnsatisfiedRuntimeRequirement [runtime=JRE, currentVersion=1.0, minVersion=2.0]");
  }

}
