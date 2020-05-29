/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2020 SonarSource SA
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
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.LanguageNotEnabled;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedDependency;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason.UnsatisfiedJreRequirement;

import static org.assertj.core.api.Assertions.assertThat;

class SkipReasonTests {

  @Test
  void testLanguageNotEnabled() {
    SkipReason.LanguageNotEnabled underTest = new LanguageNotEnabled("foo");
    // Getters
    assertThat(underTest.getNotEnabledLanguageKey()).isEqualTo("foo");
    // Equals
    assertThat(underTest).isEqualTo(underTest);
    assertThat(underTest).isNotEqualTo(IncompatiblePluginApi.INSTANCE);
    assertThat(underTest).isNotEqualTo(new LanguageNotEnabled("bar"));
    assertThat(underTest).isEqualTo(new LanguageNotEnabled("foo"));
    // HashCode
    assertThat(underTest).hasSameHashCodeAs(underTest);
    assertThat(underTest).hasSameHashCodeAs(new LanguageNotEnabled("foo"));
    // To String
    assertThat(underTest.toString()).isEqualTo("LanguageNotEnabled [languageKey=foo]");
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
  void testUnsatisfiedJreRequirement() {
    SkipReason.UnsatisfiedJreRequirement underTest = new UnsatisfiedJreRequirement("1.0", "2.0");
    // Getters
    assertThat(underTest.getMinVersion()).isEqualTo("2.0");
    assertThat(underTest.getCurrentVersion()).isEqualTo("1.0");
    // Equals
    assertThat(underTest).isEqualTo(underTest);
    assertThat(underTest).isNotEqualTo(IncompatiblePluginApi.INSTANCE);
    assertThat(underTest).isNotEqualTo(new UnsatisfiedJreRequirement("1.0", "1.0"));
    assertThat(underTest).isNotEqualTo(new UnsatisfiedJreRequirement("2.0", "1.0"));
    assertThat(underTest).isEqualTo(new UnsatisfiedJreRequirement("1.0", "2.0"));
    // HashCode
    assertThat(underTest).hasSameHashCodeAs(underTest);
    assertThat(underTest).hasSameHashCodeAs(new UnsatisfiedJreRequirement("1.0", "2.0"));
    // To String
    assertThat(underTest.toString()).isEqualTo("UnsatisfiedJreRequirement [currentVersion=1.0, minVersion=2.0]");
  }

}
